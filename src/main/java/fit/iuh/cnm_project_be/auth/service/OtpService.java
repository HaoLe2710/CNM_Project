package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.enums.OtpType;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.mail.service.MailService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OtpService {
    private final int REGISTER_EXPIRY = 15;

    RedisTemplate<Object, Object> redisTemplate;
    MailService mailService;

    public static String generateOtp() {
        SecureRandom random = new SecureRandom();
        int number = random.nextInt(1000000);
        return String.format("%06d", number);
    }

    public void sendOtp(String email, long expiryMinutes, OtpType type) {
        String otp = generateOtp();

        // Structure: otp:TYPE:email
        String key = "otp:" + type.name() + ":" + email;
        redisTemplate.opsForValue().set(key, otp, expiryMinutes, TimeUnit.MINUTES);

        try {
            mailService.sendOtpMail(email, otp, expiryMinutes, type);
            log.info("[OTP] - Successfully sent OTP code [type: {}, to: {}, expiry: {} mins]", type, email, expiryMinutes);
        } catch (Exception e) {
            log.error("[OTP] - Failed to send {} email to {}: {}", type, email, e.getMessage());
            throw new BusinessException("Failed to send verification email. Please try again.");
        }
    }

    public boolean verifyOtp(String email, String inputOtp, OtpType type) {
        String key = "otp:" + type.name() + ":" + email;
        Object savedOtp = redisTemplate.opsForValue().get(key);

        if (savedOtp == null) {
            throw new BusinessException("OTP has expired or does not exist. Please request a new one.");
        }

        if (!savedOtp.toString().equals(inputOtp)) {
            throw new BusinessException("Incorrect OTP. Please check and try again.");
        }

        // Đã verify xong, xóa mã OTP 6 số ngay lập tức để bảo mật
        redisTemplate.delete(key);
        return true;
    }

    public void validateRegisterToken(String email, String token) {
        String key = "register:token:" + email;
        Object savedToken = redisTemplate.opsForValue().get(key);
        System.out.println(key);

        if (savedToken == null) {
            throw new BusinessException("Registration session not found or expired for this email. Please verify OTP again.");
        }

        if (!savedToken.toString().equals(token)) {
            throw new BusinessException("Invalid registration token. Please try the process again.");
        }
        // Xóa Token UUID sau khi đăng ký thành công (Single-use)
        // redisTemplate.delete(key);
    }

    public String verifyOtpAndGenerateToken(String email, String inputOtp, OtpType type) {
        // 1. Gọi verify để kiểm tra mã (hàm này sẽ ném lỗi nếu mã sai/hết hạn)
        this.verifyOtp(email, inputOtp, type);

        // 2. Tạo Token UUID
        String registerToken = UUID.randomUUID().toString();

        // 3. Lưu vào Redis (15 phút)
        String key = "register:token:" + email;
        redisTemplate.opsForValue().set(key, registerToken, 15, TimeUnit.MINUTES);

        return registerToken;
    }

    public void sendRegisterOtp(String email) {
        log.info("[OTP] - Processing registration OTP request for: {}", email);
        this.sendOtp(email, REGISTER_EXPIRY, OtpType.REGISTER);
    }
}