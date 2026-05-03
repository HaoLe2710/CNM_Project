package fit.iuh.cnm_project_be.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.auth.dto.request.ConfirmChangeEmailRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ConfirmChangePhoneRequest;
import fit.iuh.cnm_project_be.auth.dto.request.SendChangeEmailOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.SendChangePhoneOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.UnlockZaloLockRequest;
import fit.iuh.cnm_project_be.auth.dto.request.UpdateZaloLockRequest;
import fit.iuh.cnm_project_be.auth.dto.request.VerifyChangeEmailOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.VerifyChangePhoneOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.VerifyZaloLockPinRequest;
import fit.iuh.cnm_project_be.auth.dto.response.AccountSecuritySummaryResponse;
import fit.iuh.cnm_project_be.auth.dto.response.ContactUpdateResponse;
import fit.iuh.cnm_project_be.auth.dto.response.OtpChallengeResponse;
import fit.iuh.cnm_project_be.auth.dto.response.OtpVerificationTokenResponse;
import fit.iuh.cnm_project_be.auth.dto.response.SecurityCheckResponse;
import fit.iuh.cnm_project_be.auth.dto.response.SecurityHistoryItemResponse;
import fit.iuh.cnm_project_be.auth.dto.response.SecurityIssueResponse;
import fit.iuh.cnm_project_be.auth.dto.response.UserQrCodeResponse;
import fit.iuh.cnm_project_be.auth.dto.response.ZaloLockChallengeResponse;
import fit.iuh.cnm_project_be.auth.dto.response.ZaloLockSettingsResponse;
import fit.iuh.cnm_project_be.auth.dto.response.ZaloLockVerifyResponse;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.enums.OtpType;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import fit.iuh.cnm_project_be.user.service.UserDeviceService;
import fit.iuh.cnm_project_be.user.service.UserService;
import fit.iuh.cnm_project_be.user.service.UserSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountSecurityService {

    private static final long CONTACT_OTP_EXPIRY_MINUTES = 15;
    private static final long QR_EXPIRY_MINUTES = 30;
    private static final long ZALO_LOCK_CHALLENGE_EXPIRY_SECONDS = 300;
    private static final long ZALO_LOCK_LOCKOUT_MINUTES = 15;
    private static final int ZALO_LOCK_MAX_ATTEMPTS = 5;
    private static final String ACCOUNT_SECURITY_SECTION = "accountSecurity";
    private static final String ZALO_LOCK_SECTION = "zaloLock";

    private final UserService userService;
    private final UserSettingService userSettingService;
    private final UserProfileRepository userProfileRepository;
    private final AccountRepository accountRepository;
    private final OtpService otpService;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final TokenRedisService tokenRedisService;
    private final UserDeviceService userDeviceService;
    private final ObjectMapper objectMapper;
    private final SecurityAuditService securityAuditService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.mock-sms-otp:true}")
    private boolean mockSmsOtpEnabled;

    @Transactional(readOnly = true)
    public AccountSecuritySummaryResponse getSummary() {
        UserProfile profile = userService.getMyProfile();
        Map<String, Object> accountSecurity = userSettingService.getMySection(ACCOUNT_SECURITY_SECTION);
        ZaloLockSettingsResponse zaloLock = getZaloLockSettings();
        List<SecurityIssueResponse> issues = new ArrayList<>();
        List<SecurityCheckResponse> checks = new ArrayList<>();

        boolean hasEmail = !isBlank(profile.getEmail());
        boolean hasPhone = !isBlank(profile.getPhone());
        boolean twoFactorEnabled = readBoolean(accountSecurity.get("twoFactorAuthEnabled"));
        boolean loginAlertsEnabled = readBoolean(accountSecurity.get("loginAlerts"));
        boolean requireDeviceApprovalEnabled = readBoolean(accountSecurity.get("requireDeviceApproval"));
        boolean zaloLockEnabled = zaloLock.isEnabled();

        checks.add(check("email-linked", "Email da lien ket", hasEmail,
                hasEmail ? "Email hien tai: " + profile.getEmail() : "Chua co email lien ket"));
        checks.add(check("phone-linked", "So dien thoai da lien ket", hasPhone,
                hasPhone ? "So dien thoai hien tai: " + profile.getPhone() : "Chua co so dien thoai lien ket"));
        checks.add(check("two-factor", "Bao mat 2 lop", twoFactorEnabled,
                twoFactorEnabled ? "Dang bat" : "Dang tat"));
        checks.add(check("login-alerts", "Canh bao dang nhap", loginAlertsEnabled,
                loginAlertsEnabled ? "Dang bat" : "Dang tat"));
        checks.add(check("device-approval", "Phe duyet dang nhap web", requireDeviceApprovalEnabled,
                requireDeviceApprovalEnabled ? "Dang bat" : "Dang tat"));
        checks.add(check("zalo-lock", "Khoa Zalo", zaloLockEnabled,
                zaloLockEnabled ? "Dang bat bang " + zaloLock.getMethod() : "Dang tat"));

        if (!hasEmail) {
            issues.add(issue(
                    "MISSING_EMAIL",
                    "MEDIUM",
                    "Email chua duoc cap nhat",
                    "Tai khoan chua co email de nhan OTP va canh bao dang nhap.",
                    "UPDATE_EMAIL",
                    "Cap nhat email"
            ));
        }

        if (!hasPhone) {
            issues.add(issue(
                    "MISSING_PHONE",
                    "LOW",
                    "So dien thoai chua duoc cap nhat",
                    "Bo sung so dien thoai de khoi phuc tai khoan nhanh hon.",
                    "UPDATE_PHONE",
                    "Cap nhat so dien thoai"
            ));
        }

        if (!twoFactorEnabled) {
            issues.add(issue(
                    "TWO_FACTOR_DISABLED",
                    "HIGH",
                    "Bao mat 2 lop dang tat",
                    "Bat bao mat 2 lop de giam rui ro mat tai khoan.",
                    "ENABLE_TWO_FACTOR",
                    "Bat bao mat 2 lop"
            ));
        }

        if (!zaloLockEnabled) {
            issues.add(issue(
                    "ZALO_LOCK_DISABLED",
                    "MEDIUM",
                    "Khoa Zalo dang tat",
                    "Bat khoa ung dung de bao ve noi dung chat khi roi may.",
                    "ENABLE_ZALO_LOCK",
                    "Bat Khoa Zalo"
            ));
        }

        if (!loginAlertsEnabled) {
            issues.add(issue(
                    "LOGIN_ALERTS_DISABLED",
                    "LOW",
                    "Canh bao dang nhap dang tat",
                    "Nen bat canh bao dang nhap de phat hien thiet bi la som hon.",
                    "ENABLE_LOGIN_ALERTS",
                    "Bat canh bao dang nhap"
            ));
        }

        if (!requireDeviceApprovalEnabled) {
            issues.add(issue(
                    "DEVICE_APPROVAL_DISABLED",
                    "MEDIUM",
                    "Phe duyet thiet bi dang tat",
                    "Nen yeu cau phe duyet thiet bi moi de han che dang nhap trai phep.",
                    "ENABLE_DEVICE_APPROVAL",
                    "Bat phe duyet thiet bi"
            ));
        }

        return AccountSecuritySummaryResponse.builder()
                .issueCount(issues.size())
                .hasIssues(!issues.isEmpty())
                .securityStrength(resolveSecurityStrength(checks))
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .accountSecurity(accountSecurity)
                .zaloLock(zaloLock)
                .checks(checks)
                .issues(issues)
                .build();
    }

    @Transactional(readOnly = true)
    public ZaloLockSettingsResponse getZaloLockSettings() {
        return toZaloLockResponse(userSettingService.getMySection(ZALO_LOCK_SECTION));
    }

    @Transactional
    public ZaloLockSettingsResponse updateZaloLock(UpdateZaloLockRequest request) {
        if (request.getEnabled() == null) {
            throw new BusinessException("enabled is required");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", request.getEnabled());

        if (!request.getEnabled()) {
            payload.put("method", "NONE");
            payload.put("pinConfigured", false);
            payload.put("biometricEnabled", false);
            payload.put("pinHash", null);
            ZaloLockSettingsResponse response = toZaloLockResponse(userSettingService.updateMySection(ZALO_LOCK_SECTION, payload));
            securityAuditService.log(userService.getCurrentUserId(), "ACCOUNT_SECURITY_ZALO_LOCK_UPDATED",
                    "Da cap nhat Khoa Zalo", "Khoa Zalo da duoc tat", null, null);
            return response;
        }

        String method = normalizeLockMethod(request.getMethod());
        payload.put("method", method);

        boolean pinConfigured = "PIN".equals(method);
        boolean biometricEnabled = "BIOMETRIC".equals(method) && Boolean.TRUE.equals(request.getBiometricEnabled());

        if ("PIN".equals(method)) {
            validatePin(request.getPin());
            payload.put("pinHash", passwordEncoder.encode(request.getPin()));
        }

        payload.put("pinConfigured", pinConfigured);
        payload.put("biometricEnabled", biometricEnabled);
        ZaloLockSettingsResponse response = toZaloLockResponse(userSettingService.updateMySection(ZALO_LOCK_SECTION, payload));
        securityAuditService.log(userService.getCurrentUserId(), "ACCOUNT_SECURITY_ZALO_LOCK_UPDATED",
                "Da cap nhat Khoa Zalo", "Khoa Zalo da duoc bat bang " + method, null, null);
        return response;
    }

    @Transactional(readOnly = true)
    public UserQrCodeResponse getMyQrCode() {
        UserProfile profile = userService.getMyProfile();
        Instant expiresAt = Instant.now().plus(QR_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "USER_PROFILE");
        payload.put("userId", profile.getUserId());
        payload.put("displayName", profile.getDisplayName());
        payload.put("username", profile.getUsername());
        payload.put("inviteLink", profile.getInviteLink());
        payload.put("expiresAt", expiresAt.toString());

        return UserQrCodeResponse.builder()
                .userId(profile.getUserId())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .inviteLink(profile.getInviteLink())
                .qrCodeUrl(profile.getQrCodeUrl())
                .qrContent(writeJson(payload))
                .expiresAt(expiresAt)
                .build();
    }

    public ZaloLockChallengeResponse createZaloLockChallenge() {
        UUID userId = userService.getCurrentUserId();
        Map<String, Object> section = userSettingService.getSection(userId, ZALO_LOCK_SECTION);
        ensureZaloLockEnabled(section);
        long lockedUntil = getLockedUntilEpochMillis(userId);
        if (lockedUntil > System.currentTimeMillis()) {
            return ZaloLockChallengeResponse.builder()
                    .challengeToken(null)
                    .expiresInSeconds(0)
                    .remainingAttempts(0)
                    .lockedUntilEpochMillis(lockedUntil)
                    .build();
        }

        String challengeToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                buildZaloLockChallengeKey(userId, challengeToken),
                "ACTIVE",
                ZALO_LOCK_CHALLENGE_EXPIRY_SECONDS,
                TimeUnit.SECONDS
        );
        return ZaloLockChallengeResponse.builder()
                .challengeToken(challengeToken)
                .expiresInSeconds(ZALO_LOCK_CHALLENGE_EXPIRY_SECONDS)
                .remainingAttempts(remainingAttempts(userId))
                .lockedUntilEpochMillis(0)
                .build();
    }

    public ZaloLockVerifyResponse verifyZaloLockPin(VerifyZaloLockPinRequest request) {
        UUID userId = userService.getCurrentUserId();
        return verifyPinAgainstSettings(userId, request.getPin(), false);
    }

    public ZaloLockVerifyResponse unlockZaloLock(UnlockZaloLockRequest request) {
        UUID userId = userService.getCurrentUserId();
        String challengeKey = buildZaloLockChallengeKey(userId, request.getChallengeToken());
        Object challenge = redisTemplate.opsForValue().get(challengeKey);
        if (challenge == null) {
            throw new BusinessException("Zalo Lock challenge is invalid or expired");
        }
        ZaloLockVerifyResponse response = verifyPinAgainstSettings(userId, request.getPin(), true);
        if (response.isSuccess()) {
            redisTemplate.delete(challengeKey);
        }
        return response;
    }

    public UserQrCodeResponse refreshMyQrCode() {
        UserQrCodeResponse response = getMyQrCode();
        securityAuditService.log(userService.getCurrentUserId(), "ACCOUNT_SECURITY_QR_REFRESHED",
                "Da lam moi QR cua toi", "Nguoi dung da tao ma QR moi cho trang account security", null, null);
        return response;
    }

    public OtpChallengeResponse sendChangeEmailOtp(SendChangeEmailOtpRequest request) {
        String newEmail = normalizeEmail(request.getNewEmail());
        UserProfile profile = userService.getMyProfile();
        validateNewEmail(newEmail, profile.getEmail());

        otpService.sendOtp(newEmail, CONTACT_OTP_EXPIRY_MINUTES, OtpType.CHANGE_EMAIL);
        securityAuditService.log(profile.getUserId(), "ACCOUNT_SECURITY_CHANGE_EMAIL_OTP_SENT",
                "Da gui OTP doi email", "OTP duoc gui toi " + maskEmail(newEmail), null, null);
        return OtpChallengeResponse.builder()
                .destination(maskEmail(newEmail))
                .deliveryChannel("EMAIL")
                .expiresInSeconds(CONTACT_OTP_EXPIRY_MINUTES * 60)
                .message("OTP has been sent to your new email")
                .build();
    }

    public OtpVerificationTokenResponse verifyChangeEmailOtp(VerifyChangeEmailOtpRequest request) {
        UUID userId = userService.getCurrentUserId();
        String newEmail = normalizeEmail(request.getNewEmail());
        UserProfile profile = userService.getMyProfile();
        validateNewEmail(newEmail, profile.getEmail());

        otpService.verifyOtp(newEmail, request.getOtp(), OtpType.CHANGE_EMAIL);
        securityAuditService.log(userId, "ACCOUNT_SECURITY_CHANGE_EMAIL_VERIFIED",
                "Da xac thuc OTP doi email", "OTP doi email moi da duoc xac thuc", null, null);

        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                buildScopedTokenKey("change:email:token", userId, newEmail),
                token,
                CONTACT_OTP_EXPIRY_MINUTES,
                TimeUnit.MINUTES
        );

        return OtpVerificationTokenResponse.builder()
                .token(token)
                .destination(maskEmail(newEmail))
                .expiresInSeconds(CONTACT_OTP_EXPIRY_MINUTES * 60)
                .build();
    }

    @Transactional
    public ContactUpdateResponse confirmChangeEmail(ConfirmChangeEmailRequest request) {
        UUID userId = userService.getCurrentUserId();
        String newEmail = normalizeEmail(request.getNewEmail());
        UserProfile profile = userService.getMyProfile();
        validateNewEmail(newEmail, profile.getEmail());
        validateScopedToken("change:email:token", userId, newEmail, request.getChangeToken());

        Account account = accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        account.setEmail(newEmail);
        account.setUsername(newEmail);
        accountRepository.save(account);

        profile.setEmail(newEmail);
        profile.setUsername(newEmail);
        profile.setUpdatedAt(Instant.now());
        userProfileRepository.save(profile);

        clearContactChangeToken("change:email:token", userId, newEmail);
        logoutEverywhere(userId);
        securityAuditService.log(userId, "ACCOUNT_SECURITY_CHANGE_EMAIL_CONFIRMED",
                "Da doi email", "Email moi da duoc cap nhat thanh " + maskEmail(newEmail), null, null);

        return ContactUpdateResponse.builder()
                .email(newEmail)
                .phone(profile.getPhone())
                .updatedAt(profile.getUpdatedAt())
                .message("Email updated successfully. Please login again.")
                .reLoginRequired(true)
                .build();
    }

    public OtpChallengeResponse sendChangePhoneOtp(SendChangePhoneOtpRequest request) {
        String newPhone = normalizePhone(request.getNewPhone());
        UserProfile profile = userService.getMyProfile();
        validateNewPhone(newPhone, profile.getPhone());

        String otpPreview = otpService.sendPhoneOtpMock(newPhone, CONTACT_OTP_EXPIRY_MINUTES, OtpType.CHANGE_PHONE);
        securityAuditService.log(profile.getUserId(), "ACCOUNT_SECURITY_CHANGE_PHONE_OTP_SENT",
                "Da gui OTP doi so dien thoai", "OTP duoc tao cho " + maskPhone(newPhone), null, null);
        return OtpChallengeResponse.builder()
                .destination(maskPhone(newPhone))
                .deliveryChannel(mockSmsOtpEnabled ? "SMS_MOCK" : "SMS")
                .expiresInSeconds(CONTACT_OTP_EXPIRY_MINUTES * 60)
                .message(mockSmsOtpEnabled
                        ? "OTP has been generated in mock SMS mode"
                        : "OTP has been sent to your new phone")
                .devOtpPreview(mockSmsOtpEnabled ? otpPreview : null)
                .build();
    }

    public OtpVerificationTokenResponse verifyChangePhoneOtp(VerifyChangePhoneOtpRequest request) {
        UUID userId = userService.getCurrentUserId();
        String newPhone = normalizePhone(request.getNewPhone());
        UserProfile profile = userService.getMyProfile();
        validateNewPhone(newPhone, profile.getPhone());

        otpService.verifyOtp(newPhone, request.getOtp(), OtpType.CHANGE_PHONE);
        securityAuditService.log(userId, "ACCOUNT_SECURITY_CHANGE_PHONE_VERIFIED",
                "Da xac thuc OTP doi so dien thoai", "OTP doi so dien thoai moi da duoc xac thuc", null, null);

        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                buildScopedTokenKey("change:phone:token", userId, newPhone),
                token,
                CONTACT_OTP_EXPIRY_MINUTES,
                TimeUnit.MINUTES
        );

        return OtpVerificationTokenResponse.builder()
                .token(token)
                .destination(maskPhone(newPhone))
                .expiresInSeconds(CONTACT_OTP_EXPIRY_MINUTES * 60)
                .build();
    }

    @Transactional
    public ContactUpdateResponse confirmChangePhone(ConfirmChangePhoneRequest request) {
        UUID userId = userService.getCurrentUserId();
        String newPhone = normalizePhone(request.getNewPhone());
        UserProfile profile = userService.getMyProfile();
        validateNewPhone(newPhone, profile.getPhone());
        validateScopedToken("change:phone:token", userId, newPhone, request.getChangeToken());

        Account account = accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        account.setPhone(newPhone);
        accountRepository.save(account);

        profile.setPhone(newPhone);
        profile.setUpdatedAt(Instant.now());
        userProfileRepository.save(profile);

        clearContactChangeToken("change:phone:token", userId, newPhone);
        securityAuditService.log(userId, "ACCOUNT_SECURITY_CHANGE_PHONE_CONFIRMED",
                "Da doi so dien thoai", "So dien thoai moi da duoc cap nhat thanh " + maskPhone(newPhone), null, null);

        return ContactUpdateResponse.builder()
                .email(profile.getEmail())
                .phone(newPhone)
                .updatedAt(profile.getUpdatedAt())
                .message("Phone updated successfully")
                .reLoginRequired(false)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SecurityHistoryItemResponse> getHistory(String month, Integer size) {
        UUID userId = userService.getCurrentUserId();
        int resolvedSize = normalizeSize(size, 50, 200);
        LocalDate monthValue = month == null || month.isBlank() ? null : LocalDate.parse(month + "-01");
        return securityAuditService.getHistory(userId, monthValue, resolvedSize).stream()
                .map(log -> SecurityHistoryItemResponse.builder()
                        .id(log.getId())
                        .eventType(log.getEventType())
                        .title(log.getTitle())
                        .detail(log.getDetail())
                        .deviceId(log.getDeviceId())
                        .platform(log.getPlatform())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }

    private SecurityIssueResponse issue(
            String code,
            String severity,
            String title,
            String description,
            String actionKey,
            String actionLabel
    ) {
        return SecurityIssueResponse.builder()
                .code(code)
                .severity(severity)
                .title(title)
                .description(description)
                .actionKey(actionKey)
                .actionLabel(actionLabel)
                .build();
    }

    private SecurityCheckResponse check(String id, String title, boolean passed, String detail) {
        return SecurityCheckResponse.builder()
                .id(id)
                .title(title)
                .passed(passed)
                .detail(detail)
                .build();
    }

    private ZaloLockSettingsResponse toZaloLockResponse(Map<String, Object> section) {
        return ZaloLockSettingsResponse.builder()
                .enabled(readBoolean(section.get("enabled")))
                .method(readString(section.get("method"), "NONE"))
                .pinConfigured(readBoolean(section.get("pinConfigured")))
                .biometricEnabled(readBoolean(section.get("biometricEnabled")))
                .build();
    }

    private void validateNewEmail(String newEmail, String currentEmail) {
        if (!newEmail.contains("@")) {
            throw new BusinessException("New email is invalid");
        }
        if (newEmail.equalsIgnoreCase(defaultString(currentEmail))) {
            throw new BusinessException("New email must be different from current email");
        }
        if (accountRepository.existsByEmailAndDeletedAtIsNull(newEmail)) {
            throw new BusinessException("Email already exists");
        }
    }

    private void validateNewPhone(String newPhone, String currentPhone) {
        if (newPhone.length() < 8) {
            throw new BusinessException("New phone is invalid");
        }
        if (newPhone.equals(defaultString(currentPhone))) {
            throw new BusinessException("New phone must be different from current phone");
        }
        if (accountRepository.existsByPhoneAndDeletedAtIsNull(newPhone)) {
            throw new BusinessException("Phone already exists");
        }
    }

    private void validatePin(String pin) {
        if (pin == null || !pin.matches("\\d{4,6}")) {
            throw new BusinessException("PIN must contain 4 to 6 digits");
        }
    }

    private ZaloLockVerifyResponse verifyPinAgainstSettings(UUID userId, String pin, boolean markUnlocked) {
        Map<String, Object> section = userSettingService.getSection(userId, ZALO_LOCK_SECTION);
        ensureZaloLockEnabled(section);

        long lockedUntil = getLockedUntilEpochMillis(userId);
        if (lockedUntil > System.currentTimeMillis()) {
            return ZaloLockVerifyResponse.builder()
                    .success(false)
                    .remainingAttempts(0)
                    .lockedUntilEpochMillis(lockedUntil)
                    .message("Zalo Lock is temporarily locked")
                    .build();
        }

        String pinHash = readString(section.get("pinHash"), null);
        if (pinHash == null || pinHash.isBlank()) {
            throw new BusinessException("PIN is not configured for Zalo Lock");
        }

        if (!passwordEncoder.matches(pin, pinHash)) {
            int remainingAttempts = registerFailedPinAttempt(userId);
            long newLockedUntil = getLockedUntilEpochMillis(userId);
            return ZaloLockVerifyResponse.builder()
                    .success(false)
                    .remainingAttempts(remainingAttempts)
                    .lockedUntilEpochMillis(newLockedUntil)
                    .message(remainingAttempts > 0 ? "Incorrect PIN" : "Too many failed attempts")
                    .build();
        }

        clearFailedPinAttempts(userId);
        if (markUnlocked) {
            securityAuditService.log(userId, "ACCOUNT_SECURITY_ZALO_LOCK_UNLOCKED",
                    "Da mo Khoa Zalo", "Nguoi dung da mo khoa thanh cong bang PIN", null, null);
        }
        return ZaloLockVerifyResponse.builder()
                .success(true)
                .remainingAttempts(ZALO_LOCK_MAX_ATTEMPTS)
                .lockedUntilEpochMillis(0)
                .message("PIN verified successfully")
                .build();
    }

    private void ensureZaloLockEnabled(Map<String, Object> section) {
        if (!readBoolean(section.get("enabled"))) {
            throw new BusinessException("Zalo Lock is disabled");
        }
    }

    private int registerFailedPinAttempt(UUID userId) {
        String attemptsKey = buildZaloLockAttemptsKey(userId);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        redisTemplate.expire(attemptsKey, ZALO_LOCK_LOCKOUT_MINUTES, TimeUnit.MINUTES);
        long attemptCount = attempts == null ? 0 : attempts;
        if (attemptCount >= ZALO_LOCK_MAX_ATTEMPTS) {
            long lockedUntil = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ZALO_LOCK_LOCKOUT_MINUTES);
            redisTemplate.opsForValue().set(
                    buildZaloLockLockoutKey(userId),
                    String.valueOf(lockedUntil),
                    ZALO_LOCK_LOCKOUT_MINUTES,
                    TimeUnit.MINUTES
            );
            securityAuditService.log(userId, "ACCOUNT_SECURITY_ZALO_LOCK_LOCKED",
                    "Khoa Zalo tam khoa", "PIN nhap sai qua so lan cho phep", null, null);
            return 0;
        }
        return Math.max(0, ZALO_LOCK_MAX_ATTEMPTS - (int) attemptCount);
    }

    private void clearFailedPinAttempts(UUID userId) {
        redisTemplate.delete(buildZaloLockAttemptsKey(userId));
        redisTemplate.delete(buildZaloLockLockoutKey(userId));
    }

    private long getLockedUntilEpochMillis(UUID userId) {
        Object value = redisTemplate.opsForValue().get(buildZaloLockLockoutKey(userId));
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int remainingAttempts(UUID userId) {
        Object attemptsValue = redisTemplate.opsForValue().get(buildZaloLockAttemptsKey(userId));
        int attempts = 0;
        if (attemptsValue != null) {
            try {
                attempts = Integer.parseInt(String.valueOf(attemptsValue));
            } catch (NumberFormatException ignored) {
                attempts = 0;
            }
        }
        return Math.max(0, ZALO_LOCK_MAX_ATTEMPTS - attempts);
    }

    private String buildZaloLockAttemptsKey(UUID userId) {
        return "zalo-lock:attempts:" + userId;
    }

    private String buildZaloLockLockoutKey(UUID userId) {
        return "zalo-lock:lockout:" + userId;
    }

    private String buildZaloLockChallengeKey(UUID userId, String challengeToken) {
        return "zalo-lock:challenge:" + userId + ":" + challengeToken;
    }

    private void validateScopedToken(String prefix, UUID userId, String targetValue, String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        Object savedToken = redisTemplate.opsForValue().get(buildScopedTokenKey(prefix, userId, targetValue));
        if (savedToken == null) {
            throw new BusinessException("Change token is invalid or expired");
        }
        if (!savedToken.toString().equals(token)) {
            throw new BusinessException("Change token is invalid or expired");
        }
    }

    private void clearContactChangeToken(String prefix, UUID userId, String targetValue) {
        redisTemplate.delete(buildScopedTokenKey(prefix, userId, targetValue));
    }

    private String buildScopedTokenKey(String prefix, UUID userId, String targetValue) {
        return prefix + ":" + userId + ":" + targetValue;
    }

    private void logoutEverywhere(UUID userId) {
        tokenRedisService.deleteRefreshTokensByScope(userId, "web");
        tokenRedisService.deleteRefreshTokensByScope(userId, "android");
        tokenRedisService.deleteRefreshTokensByScope(userId, "ios");
        userDeviceService.deleteAllDevices(userId);
    }

    private String normalizeEmail(String email) {
        String normalized = defaultString(email).trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BusinessException("New email is required");
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        String normalized = defaultString(phone).trim().replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            throw new BusinessException("New phone is required");
        }
        return normalized;
    }

    private String normalizeLockMethod(String method) {
        String normalized = defaultString(method).trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BusinessException("Lock method is required when enabling Zalo Lock");
        }
        if (!List.of("PIN", "BIOMETRIC").contains(normalized)) {
            throw new BusinessException("Lock method must be PIN or BIOMETRIC");
        }
        return normalized;
    }

    private String resolveSecurityStrength(List<SecurityCheckResponse> checks) {
        long passedCount = checks.stream().filter(SecurityCheckResponse::isPassed).count();
        if (passedCount >= 5) {
            return "STRONG";
        }
        if (passedCount >= 3) {
            return "MEDIUM";
        }
        return "WEAK";
    }

    private int normalizeSize(Integer size, int defaultValue, int maxValue) {
        if (size == null || size <= 0) {
            return defaultValue;
        }
        return Math.min(size, maxValue);
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private String readString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException("Unable to build QR content");
        }
    }

    private String maskEmail(String email) {
        String[] parts = email.split("@", 2);
        if (parts.length != 2) {
            return email;
        }
        String local = parts[0];
        if (local.length() <= 2) {
            return local.charAt(0) + "***@" + parts[1];
        }
        return local.substring(0, 2) + "***@" + parts[1];
    }

    private String maskPhone(String phone) {
        if (phone.length() <= 4) {
            return "***" + phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
