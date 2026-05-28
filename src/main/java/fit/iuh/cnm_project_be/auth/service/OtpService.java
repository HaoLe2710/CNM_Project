package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.entity.AuthOtpCode;
import fit.iuh.cnm_project_be.auth.enums.OtpType;
import fit.iuh.cnm_project_be.auth.repository.AuthOtpCodeRepository;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.mail.service.MailService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OtpService {

    private static final int REGISTER_EXPIRY = 15;
    private static final long USED_OTP_RETENTION_DAYS = 7;

    AuthOtpCodeRepository authOtpCodeRepository;
    RedisTemplate<Object, Object> redisTemplate;
    MailService mailService;

    public static String generateOtp() {
        SecureRandom random = new SecureRandom();
        int number = random.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    @Transactional
    public void sendOtp(String identifier, long expiryMinutes, OtpType type) {
        String otp = generateOtp();
        persistOtp(identifier, otp, expiryMinutes, type);

        try {
            mailService.sendOtpMail(identifier, otp, expiryMinutes, type);
            log.info("[OTP] - Successfully sent OTP code [type: {}, to: {}, expiry: {} mins]", type, identifier, expiryMinutes);
        } catch (Exception e) {
            log.error("[OTP] - Failed to send {} email to {}: {}", type, identifier, e.getMessage());
            throw new BusinessException("Failed to send verification email. Please try again.");
        }
    }

    @Transactional
    public boolean verifyOtp(String identifier, String inputOtp, OtpType type) {
        purgeOldCodes();

        AuthOtpCode savedOtp = authOtpCodeRepository
                .findFirstByIdentifierAndOtpTypeAndUsedAtIsNullOrderByCreatedAtDesc(identifier, type)
                .orElseThrow(() -> new BusinessException("OTP has expired or does not exist. Please request a new one."));

        if (savedOtp.getExpiresAt().isBefore(Instant.now())) {
            savedOtp.setUsedAt(Instant.now());
            authOtpCodeRepository.save(savedOtp);
            throw new BusinessException("OTP has expired or does not exist. Please request a new one.");
        }

        if (!savedOtp.getOtpCode().equals(inputOtp)) {
            throw new BusinessException("Incorrect OTP. Please check and try again.");
        }

        savedOtp.setUsedAt(Instant.now());
        authOtpCodeRepository.save(savedOtp);
        return true;
    }

    public void validateRegisterToken(String email, String token) {
        String key = "register:token:" + email;
        Object savedToken = redisTemplate.opsForValue().get(key);

        if (savedToken == null) {
            throw new BusinessException("Registration session not found or expired for this email. Please verify OTP again.");
        }

        if (!savedToken.toString().equals(token)) {
            throw new BusinessException("Invalid registration token. Please try the process again.");
        }
    }

    @Transactional
    public String verifyOtpAndGenerateToken(String email, String inputOtp, OtpType type) {
        verifyOtp(email, inputOtp, type);

        String registerToken = UUID.randomUUID().toString();
        String key = "register:token:" + email;
        redisTemplate.opsForValue().set(key, registerToken, 15, TimeUnit.MINUTES);

        return registerToken;
    }

    @Transactional
    public void sendRegisterOtp(String email) {
        log.info("[OTP] - Processing registration OTP request for: {}", email);
        sendOtp(email, REGISTER_EXPIRY, OtpType.REGISTER);
    }

    @Transactional
    public String sendPhoneOtpMock(String phone, long expiryMinutes, OtpType type) {
        String otp = generateOtp();
        persistOtp(phone, otp, expiryMinutes, type);
        log.warn("[OTP-MOCK] - Mock phone OTP generated [type: {}, phone: {}, otp: {}, expiry: {} mins]",
                type, phone, otp, expiryMinutes);
        return otp;
    }

    private void persistOtp(String identifier, String otp, long expiryMinutes, OtpType type) {
        Instant now = Instant.now();
        authOtpCodeRepository.markActiveCodesUsed(identifier, type, now);

        AuthOtpCode authOtpCode = new AuthOtpCode();
        authOtpCode.setIdentifier(identifier);
        authOtpCode.setOtpCode(otp);
        authOtpCode.setOtpType(type);
        authOtpCode.setExpiresAt(now.plusSeconds(expiryMinutes * 60));
        authOtpCodeRepository.save(authOtpCode);

        purgeOldCodes();
    }

    private void purgeOldCodes() {
        Instant now = Instant.now();
        authOtpCodeRepository.purgeExpiredOrOldUsedCodes(
                now,
                now.minusSeconds(TimeUnit.DAYS.toSeconds(USED_OTP_RETENTION_DAYS))
        );
    }
}
