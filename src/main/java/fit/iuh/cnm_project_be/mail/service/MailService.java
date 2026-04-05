package fit.iuh.cnm_project_be.mail.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MailService {

    final JavaMailSender mailSender;

    @Value("${app.mail.from-address:}")
    String fromAddress;

    public void sendTextMail(String to, String subject, String content) {
        if (to == null || to.isBlank()) {
            throw new BusinessException("Recipient email cannot be empty");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new BusinessException("Mail sender is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to.trim());
        message.setSubject(subject == null ? "" : subject.trim());
        message.setText(content == null ? "" : content);

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Send mail failed to {}: {}", to, ex.getMessage(), ex);
            throw new BusinessException("Send mail failed");
        }
    }

    public void sendOtpMail(String to, String otpCode, int expireMinutes) {
        String subject = "Ma OTP xac thuc";
        String body = "Ma OTP cua ban la: " + otpCode + "\n"
                + "Ma co hieu luc trong " + expireMinutes + " phut.";
        sendTextMail(to, subject, body);
    }
}