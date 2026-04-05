package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.mail.service.MailService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegisterOtpService {

    int OTP_EXPIRE_MINUTES = 5;
    MailService mailService;
    Map<String, OtpRecord> otpStore = new ConcurrentHashMap<>();

    public void sendOtp(String email) {
        String normalizedEmail = normalize(email);
        cleanupIfExpired(normalizedEmail);

        String otpCode = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        Instant expiresAt = Instant.now().plus(OTP_EXPIRE_MINUTES, ChronoUnit.MINUTES);

        otpStore.put(normalizedEmail, OtpRecord.builder()
                .otp(otpCode)
                .expiresAt(expiresAt)
                .verified(false)
                .verificationToken(null)
                .build());

        mailService.sendOtpMail(normalizedEmail, otpCode, OTP_EXPIRE_MINUTES);
    }

    public String verifyOtp(String email, String otpCode) {
        String normalizedEmail = normalize(email);
        OtpRecord record = otpStore.get(normalizedEmail);
        if (record == null || record.isExpired()) {
            otpStore.remove(normalizedEmail);
            throw new BusinessException("OTP is invalid or expired");
        }

        if (!record.getOtp().equals(otpCode)) {
            throw new BusinessException("OTP is incorrect");
        }

        String verificationToken = UUID.randomUUID().toString();
        record.setVerified(true);
        record.setVerificationToken(verificationToken);
        otpStore.put(normalizedEmail, record);

        return verificationToken;
    }

    public void consumeVerification(String email, String verificationToken) {
        String normalizedEmail = normalize(email);
        OtpRecord record = otpStore.get(normalizedEmail);
        if (record == null || record.isExpired()) {
            otpStore.remove(normalizedEmail);
            throw new BusinessException("OTP verification is expired");
        }

        if (!record.isVerified() || record.getVerificationToken() == null
                || !record.getVerificationToken().equals(verificationToken)) {
            throw new BusinessException("OTP verification token is invalid");
        }

        otpStore.remove(normalizedEmail);
    }

    private String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("Email cannot be empty");
        }
        return email.trim().toLowerCase();
    }

    private void cleanupIfExpired(String normalizedEmail) {
        OtpRecord current = otpStore.get(normalizedEmail);
        if (current != null && current.isExpired()) {
            otpStore.remove(normalizedEmail);
        }
    }

    @lombok.Builder
    @lombok.Data
    static class OtpRecord {
        String otp;
        Instant expiresAt;
        boolean verified;
        String verificationToken;

        boolean isExpired() {
            return expiresAt == null || Instant.now().isAfter(expiresAt);
        }
    }
}