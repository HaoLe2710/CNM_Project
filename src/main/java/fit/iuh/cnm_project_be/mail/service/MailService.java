package fit.iuh.cnm_project_be.mail.service;

import fit.iuh.cnm_project_be.auth.enums.OtpType;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MailService {
    final JavaMailSender mailSender;

    @Value("${app.mail.from-address:}")
    String fromAddress;

    @Value("${app.mail.from-name:Zalo App}")
    String fromName;

    @Value("${app.mail.reply-to:}")
    String replyTo;

    @Value("${spring.mail.username:}")
    String smtpUsername;

    public void sendTextMail(String to, String subject, String content) {
        if (to == null || to.isBlank()) {
            throw new BusinessException("Recipient email cannot be empty");
        }

        String effectiveFrom = resolveFromAddress();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(effectiveFrom);
        message.setTo(to.trim());
        message.setSubject(subject == null ? "" : subject.trim());
        message.setText(content == null ? "" : content);

        if (replyTo != null && !replyTo.isBlank()) {
            message.setReplyTo(replyTo);
        }

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Send mail failed to {}: {}", to, ex.getMessage(), ex);
            throw new BusinessException("Send mail failed");
        }
    }

    public void sendOtpMail(String to, String otp, long expiryMinutes, OtpType type) throws Exception {
        if (to == null || to.isBlank()) {
            throw new BusinessException("Recipient email cannot be empty");
        }

        MimeMessage message = mailSender.createMimeMessage();
        String effectiveFrom = resolveFromAddress();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        String subject;
        String actionName;

        switch (type) {
            case REGISTER -> {
                subject = "[Zalo App] Ma xac thuc dang ky tai khoan";
                actionName = "Dang ky tai khoan";
            }
            case FORGOT_PASSWORD -> {
                subject = "[Zalo App] Ma xac thuc dat lai mat khau";
                actionName = "Dat lai mat khau";
            }
            case CHANGE_PASSWORD -> {
                subject = "[Zalo App] Ma xac thuc doi mat khau";
                actionName = "Doi mat khau";
            }
            case CHANGE_EMAIL -> {
                subject = "[Zalo App] Ma xac thuc doi email";
                actionName = "Doi email tai khoan";
            }
            case CHANGE_PHONE -> {
                subject = "[Zalo App] Ma xac thuc doi so dien thoai";
                actionName = "Doi so dien thoai";
            }
            default -> {
                subject = "[Zalo App] Ma xac thuc he thong";
                actionName = "Xac thuc danh tinh";
            }
        }

        applySender(helper, effectiveFrom);
        helper.setTo(to.trim());
        helper.setSubject(subject);

        String content = String.format(
                "<div style='font-family: Helvetica, Arial, sans-serif; max-width: 500px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;'>"
                        + "<h2 style='color: #0068ff; text-align: center;'>Zalo App Verification</h2>"
                        + "<p>Chao ban,</p>"
                        + "<p>Ban dang thuc hien yeu cau: <b style='color: #333;'>%s</b>.</p>"
                        + "<div style='background: #f0f7ff; border: 1px solid #0068ff; color: #0068ff; font-size: 32px; font-weight: bold; text-align: center; padding: 15px; margin: 20px 0; letter-spacing: 5px;'>%s</div>"
                        + "<p>Ma OTP nay co hieu luc trong <b>%d phut</b>.</p>"
                        + "<hr style='border: none; border-top: 1px solid #eee;' />"
                        + "<p style='font-size: 12px; color: #888;'>Neu ban khong thuc hien yeu cau nay, vui long bo qua email nay hoac lien he bo phan ho tro Zalo App.</p>"
                        + "<p style='font-size: 12px; color: #888; text-align: center;'>&copy; 2026 Zalo App. All rights reserved.</p>"
                        + "</div>",
                actionName, otp, expiryMinutes
        );

        helper.setText(content, true);
        mailSender.send(message);
    }

    private void applySender(MimeMessageHelper helper, String effectiveFrom)
            throws MessagingException, UnsupportedEncodingException {
        if (fromName != null && !fromName.isBlank()) {
            helper.setFrom(effectiveFrom, fromName);
        } else {
            helper.setFrom(effectiveFrom);
        }

        if (replyTo != null && !replyTo.isBlank()) {
            helper.setReplyTo(replyTo);
        }
    }

    private String resolveFromAddress() {
        if (fromAddress != null && !fromAddress.isBlank() && !fromAddress.contains("yourdomain.com")) {
            return fromAddress;
        }
        if (smtpUsername != null && !smtpUsername.isBlank()) {
            log.warn("app.mail.from-address is not configured/verified, fallback to SMTP username: {}", smtpUsername);
            return smtpUsername;
        }
        throw new BusinessException("Mail sender is not configured");
    }
}
