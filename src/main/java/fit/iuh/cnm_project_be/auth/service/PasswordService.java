package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.request.ConfirmPasswordChangeRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ForgotPasswordResetRequest;
import fit.iuh.cnm_project_be.auth.dto.response.ForgotPasswordVerifyOtpResponse;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.enums.OtpType;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.user.service.UserDeviceService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service xử lý quản lý mật khẩu toàn diện:
 * 
 * 1. CHANGE PASSWORD (2 steps - requires authentication):
 *    - Xác thực mật khẩu hiện tại -> cấp changePasswordToken
 *    - Sử dụng token để đổi mật khẩu
 * 
 * 2. FORGOT PASSWORD (3 steps - public):
 *    - Gửi OTP để xác thực email/phone
 *    - Xác thực OTP -> cấp resetToken
 *    - Reset mật khẩu bằng resetToken
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class PasswordService {

    AccountRepository accountRepository;
    OtpService otpService;
    PasswordEncoder passwordEncoder;
    RedisTemplate<Object, Object> redisTemplate;
    TokenRedisService tokenRedisService;
    UserDeviceService userDeviceService;

    private static final int CHANGE_PASSWORD_TOKEN_EXPIRY = 10; // 10 phút
    private static final int FORGOT_PASSWORD_EXPIRY = 15; // 15 phút

    /**
     * Xác thực mật khẩu hiện tại và tạo changePasswordToken
     *
     * @param userId          ID của user
     * @param currentPassword Mật khẩu hiện tại
     * @return Token để sử dụng trong bước đổi mật khẩu
     * @throws NotFoundException Nếu account không tồn tại
     * @throws BusinessException Nếu mật khẩu không đúng
     */
    public String verifyCurrentPassword(UUID userId, String currentPassword) {
        // 1. Lấy Account
        Account account = accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.warn("[ChangePasswordToken] - Account not found for userId: {}", userId);
                    return new NotFoundException("Account not found");
                });

        // 2. Xác thực mật khẩu hiện tại
        if (!passwordEncoder.matches(currentPassword, account.getPassword())) {
            log.warn("[ChangePasswordToken] - Current password incorrect for userId: {}", userId);
            throw new BusinessException("Current password is incorrect");
        }

        // 3. Tạo token duy nhất
        String changePasswordToken = UUID.randomUUID().toString();
        String key = "change:password:token:" + userId;

        redisTemplate.opsForValue().set(key, changePasswordToken, CHANGE_PASSWORD_TOKEN_EXPIRY, TimeUnit.MINUTES);

        log.info("[ChangePasswordToken] - Change password token created for userId: {}", userId);
        return changePasswordToken;
    }

    /**
     * Đổi mật khẩu sử dụng changePasswordToken
     *
     * @param userId                      ID của user
     * @param confirmPasswordChangeRequest Request chứa token và mật khẩu mới
     * @throws BusinessException Nếu token không hợp lệ, hết hạn, hoặc mật khẩu không hợp lệ
     * @throws NotFoundException Nếu account không tồn tại
     */
    @Transactional
    public void confirmPasswordChange(UUID userId, ConfirmPasswordChangeRequest confirmPasswordChangeRequest) {
        String token = confirmPasswordChangeRequest.getChangePasswordToken().trim();
        String newPassword = confirmPasswordChangeRequest.getNewPassword();

        // 1. Lấy Account
        Account account = accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.warn("[ChangePasswordToken] - Account not found for userId: {}", userId);
                    return new NotFoundException("Account not found");
                });

        // 2. Xác thực token
        validateChangePasswordToken(userId, token);

        // 3. Kiểm tra mật khẩu mới không trùng với mật khẩu cũ
        if (passwordEncoder.matches(newPassword, account.getPassword())) {
            log.warn("[ChangePasswordToken] - New password same as old password for userId: {}", userId);
            throw new BusinessException("New password must be different from current password");
        }

        // 4. Mã hóa mật khẩu mới
        String encodedPassword = passwordEncoder.encode(newPassword);

        // 5. Cập nhật mật khẩu
        account.setPassword(encodedPassword);
        accountRepository.save(account);

        // 6. Xóa token (single-use)
        redisTemplate.delete("change:password:token:" + userId);

        log.info("[ChangePasswordToken] - Password successfully changed for userId: {}", userId);
    }

    /**
     * Xác thực token có hợp lệ không
     *
     * @param userId ID của user
     * @param token  Change password token
     * @throws BusinessException Nếu token không hợp lệ hoặc hết hạn
     */
    private void validateChangePasswordToken(UUID userId, String token) {
        String key = "change:password:token:" + userId;
        Object savedToken = redisTemplate.opsForValue().get(key);

        if (savedToken == null) {
            log.warn("[ChangePasswordToken] - Token not found or expired for userId: {}", userId);
            throw new BusinessException("Change password token is invalid or has expired. Please verify your current password again.");
        }

        if (!savedToken.toString().equals(token)) {
            log.warn("[ChangePasswordToken] - Token mismatch for userId: {}", userId);
            throw new BusinessException("Invalid change password token. Please try again.");
        }
    }

    // ==================== FORGOT PASSWORD METHODS ====================

    /**
     * Gửi OTP để xác thực email/phone khi quên mật khẩu
     *
     * @param identifier Email hoặc phone
     * @throws BusinessException Nếu email/phone không tồn tại
     */
    public void sendForgotPasswordOtp(String identifier) {
        String normalizedIdentifier = normalizeIdentifier(identifier);

        log.info("[ForgotPassword] - Processing OTP request for: {}", normalizedIdentifier);

        // 1. Kiểm tra email/phone có tồn tại không
        Account account = accountRepository.findByEmailOrPhone(normalizedIdentifier, normalizedIdentifier)
                .orElseThrow(() -> {
                    log.warn("[ForgotPassword] - Identifier {} not found in system", normalizedIdentifier);
                    return new BusinessException("Email or phone not found in system");
                });

        // 2. Gửi OTP
        otpService.sendOtp(normalizedIdentifier, FORGOT_PASSWORD_EXPIRY, OtpType.FORGOT_PASSWORD);

        log.info("[ForgotPassword] - OTP sent successfully to: {}", normalizedIdentifier);
    }

    /**
     * Xác thực OTP và cấp resetToken
     *
     * @param identifier Email hoặc phone
     * @param otp        Mã OTP 6 chữ số
     * @return resetToken để sử dụng trong bước reset password
     * @throws BusinessException Nếu OTP không đúng/hết hạn
     */
    public ForgotPasswordVerifyOtpResponse verifyForgotPasswordOtp(String identifier, String otp) {
        String normalizedIdentifier = normalizeIdentifier(identifier);

        log.info("[ForgotPassword] - Verifying OTP for: {}", normalizedIdentifier);

        // 1. Xác thực OTP thông qua OtpService (hàm này tự xóa OTP nếu đúng)
        otpService.verifyOtp(normalizedIdentifier, otp, OtpType.FORGOT_PASSWORD);

        // 2. Tạo resetToken
        String resetToken = UUID.randomUUID().toString();
        String key = "reset:password:token:" + normalizedIdentifier;

        redisTemplate.opsForValue().set(key, resetToken, FORGOT_PASSWORD_EXPIRY, TimeUnit.MINUTES);

        log.info("[ForgotPassword] - Reset token generated for: {}", normalizedIdentifier);

        return ForgotPasswordVerifyOtpResponse.builder()
                .resetToken(resetToken)
                .build();
    }

    /**
     * Reset mật khẩu sử dụng resetToken
     *
     * @param request Chứa identifier, resetToken, newPassword, confirmPassword
     * @throws BusinessException Nếu token không hợp lệ, password không match, hoặc mật khẩu trùng cũ
     * @throws NotFoundException Nếu account không tồn tại
     */
    @Transactional
    public void resetPassword(ForgotPasswordResetRequest request) {
        String normalizedIdentifier = normalizeIdentifier(request.getIdentifier());
        String resetToken = request.getResetToken().trim();
        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();

        log.info("[ForgotPassword] - Reset password request for: {}", normalizedIdentifier);

        // 1. Kiểm tra password match
        if (!newPassword.equals(confirmPassword)) {
            log.warn("[ForgotPassword] - Password mismatch for: {}", normalizedIdentifier);
            throw new BusinessException("Passwords do not match");
        }

        // 2. Lấy Account
        Account account = accountRepository.findByEmailOrPhone(normalizedIdentifier, normalizedIdentifier)
                .orElseThrow(() -> {
                    log.warn("[ForgotPassword] - Account not found for: {}", normalizedIdentifier);
                    return new NotFoundException("Account not found");
                });

        // 3. Xác thực resetToken
        validateResetToken(normalizedIdentifier, resetToken);

        // 4. Kiểm tra mật khẩu mới không trùng với mật khẩu cũ
        if (passwordEncoder.matches(newPassword, account.getPassword())) {
            log.warn("[ForgotPassword] - New password same as old for: {}", normalizedIdentifier);
            throw new BusinessException("New password must be different from current password");
        }

        // 5. Mã hóa mật khẩu mới
        String encodedPassword = passwordEncoder.encode(newPassword);

        // 6. Cập nhật mật khẩu trong Account
        account.setPassword(encodedPassword);
        accountRepository.save(account);

        // 7. Xóa resetToken (single-use)
        redisTemplate.delete("reset:password:token:" + normalizedIdentifier);

        // 8. Xóa tất cả refresh tokens -> force logout all devices
        tokenRedisService.deleteRefreshTokensByScope(account.getUserId(), "web");
        tokenRedisService.deleteRefreshTokensByScope(account.getUserId(), "android");
        tokenRedisService.deleteRefreshTokensByScope(account.getUserId(), "ios");
        userDeviceService.deleteAllDevices(account.getUserId());
        log.info("[ForgotPassword] - All refresh tokens deleted for userId: {}", account.getUserId());

        log.info("[ForgotPassword] - Password successfully reset for: {}", normalizedIdentifier);
    }

    /**
     * Xác thực resetToken có hợp lệ không
     *
     * @param identifier Email hoặc phone
     * @param token      Reset token
     * @throws BusinessException Nếu token không hợp lệ hoặc hết hạn
     */
    private void validateResetToken(String identifier, String token) {
        String key = "reset:password:token:" + identifier;
        Object savedToken = redisTemplate.opsForValue().get(key);

        if (savedToken == null) {
            log.warn("[ForgotPassword] - Reset token not found or expired for: {}", identifier);
            throw new BusinessException("Reset token is invalid or has expired. Please request a new OTP.");
        }

        if (!savedToken.toString().equals(token)) {
            log.warn("[ForgotPassword] - Reset token mismatch for: {}", identifier);
            throw new BusinessException("Invalid reset token. Please try again.");
        }
    }

    /**
     * Normalize identifier (email hoặc phone)
     * - Email: lowercase
     * - Phone: trim, remove spaces
     *
     * @param identifier Email hoặc phone
     * @return Normalized string
     */
    private String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BusinessException("Identifier cannot be empty");
        }

        String normalized = identifier.trim();

        // Nếu là email (chứa @)
        if (normalized.contains("@")) {
            return normalized.toLowerCase();
        }

        // Nếu là phone, remove spaces
        return normalized.replaceAll("\\s+", "");
    }
}
