package fit.iuh.cnm_project_be.mail.service;

import fit.iuh.cnm_project_be.auth.enums.OtpType;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
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


    public void sendOtpMail(String to, String otp, long expiryMinutes, OtpType type) throws Exception {
        if (to == null || to.isBlank()) {
            throw new BusinessException("Recipient email cannot be empty");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new BusinessException("Mail sender is not configured");
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // 1. Định nghĩa các biến thay đổi theo Type
        String subject;
        String actionName; // Tên hành động cụ thể (ví dụ: Đặt lại mật khẩu)

        switch (type) {
            case REGISTER -> {
                subject = "[Zalo] Mã xác thực đăng ký tài khoản";
                actionName = "Đăng ký tài khoản";
            }
            case FORGOT_PASSWORD -> {
                subject = "[Zalo] Mã xác thực đặt lại mật khẩu";
                actionName = "Đặt lại mật khẩu";
            }
            case CHANGE_PASSWORD -> {
                subject = "[Zalo] Mã xác thực đổi mật khẩu";
                actionName = "Đổi mật khẩu";
            }
            default -> {
                subject = "[Zalo] Mã xác thực hệ thống";
                actionName = "Xác thực danh tính";
            }
        }

        helper.setFrom(fromAddress);
        helper.setTo(to.trim());
        helper.setSubject(subject);

        // 2. Nội dung HTML với giao diện xanh đặc trưng của Zalo
        String content = String.format(
                "<div style='font-family: Helvetica, Arial, sans-serif; max-width: 500px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;'>" +
                        "  <h2 style='color: #0068ff; text-align: center;'>Zalo Verification</h2>" +
                        "  <p>Chào bạn,</p>" +
                        "  <p>Bạn đang thực hiện yêu cầu: <b style='color: #333;'>%s</b>.</p>" + // Thay đổi theo actionName
                        "  <div style='background: #f0f7ff; border: 1px solid #0068ff; color: #0068ff; font-size: 32px; font-weight: bold; text-align: center; padding: 15px; margin: 20px 0; letter-spacing: 5px;'>" +
                        "    %s" + // Mã OTP
                        "  </div>" +
                        "  <p>Mã OTP này có hiệu lực trong <b>%d phút</b>.</p>" +
                        "  <hr style='border: none; border-top: 1px solid #eee;' />" +
                        "  <p style='font-size: 12px; color: #888;'>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email hoặc liên hệ bộ phận hỗ trợ Zalo.</p>" +
                        "  <p style='font-size: 12px; color: #888; text-align: center;'>© 2026 Zalo. All rights reserved.</p>" +
                        "</div>",
                actionName, otp, expiryMinutes
        );

        helper.setText(content, true);
        mailSender.send(message);
    }
}