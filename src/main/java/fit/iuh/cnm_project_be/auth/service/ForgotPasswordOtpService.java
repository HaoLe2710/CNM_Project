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
public class ForgotPasswordOtpService {

    int OTP_EXPIRE_MINUTES = 5;
    MailService mailService;
    Map<UUID, OtpRecord> otpStore = new ConcurrentHashMap<>();

    public void sendOtp(UUID accountId, String email) {
        if (accountId == null) {
            throw new BusinessException("Account is invalid");
        }

        String normalizedEmail = normalizeEmail(email);
        cleanupIfExpired(accountId);

        String otpCode = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        Instant expiresAt = Instant.now().plus(OTP_EXPIRE_MINUTES, ChronoUnit.MINUTES);

        otpStore.put(accountId, OtpRecord.builder()
                .email(normalizedEmail)
                .otp(otpCode)
                .expiresAt(expiresAt)
                .verified(false)
                .resetToken(null)
                .build());

        mailService.sendOtpMail(normalizedEmail, otpCode, OTP_EXPIRE_MINUTES);
    }

    public String verifyOtp(UUID accountId, String email, String otpCode) {
        if (accountId == null) {
            throw new BusinessException("Account is invalid");
        }

        String normalizedEmail = normalizeEmail(email);
        OtpRecord record = otpStore.get(accountId);
        if (record == null || record.isExpired()) {
            otpStore.remove(accountId);
            throw new BusinessException("OTP is invalid or expired");
        }

        if (!normalizedEmail.equals(record.getEmail())) {
            throw new BusinessException("OTP request is invalid");
        }

        if (!record.getOtp().equals(otpCode)) {
            throw new BusinessException("OTP is incorrect");
        }

        String resetToken = UUID.randomUUID().toString();
        record.setVerified(true);
        record.setResetToken(resetToken);
        otpStore.put(accountId, record);
        return resetToken;
    }

    public void consumeResetToken(UUID accountId, String email, String resetToken) {
        if (accountId == null) {
            throw new BusinessException("Account is invalid");
        }

        String normalizedEmail = normalizeEmail(email);
        OtpRecord record = otpStore.get(accountId);
        if (record == null || record.isExpired()) {
            otpStore.remove(accountId);
            throw new BusinessException("Reset password verification is expired");
        }

        if (!normalizedEmail.equals(record.getEmail())) {
            throw new BusinessException("Reset password request is invalid");
        }

        if (!record.isVerified() || record.getResetToken() == null || !record.getResetToken().equals(resetToken)) {
            throw new BusinessException("Reset token is invalid");
        }

        otpStore.remove(accountId);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("Linked email is invalid");
        }
        return email.trim().toLowerCase();
    }

    private void cleanupIfExpired(UUID accountId) {
        OtpRecord current = otpStore.get(accountId);
        if (current != null && current.isExpired()) {
            otpStore.remove(accountId);
        }
    }

    @lombok.Builder
    @lombok.Data
    static class OtpRecord {
        String email;
        String otp;
        Instant expiresAt;
        boolean verified;
        String resetToken;

        boolean isExpired() {
            return expiresAt == null || Instant.now().isAfter(expiresAt);
        }
    }
}