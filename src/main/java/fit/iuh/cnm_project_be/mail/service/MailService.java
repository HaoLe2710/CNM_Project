package fit.iuh.cnm_project_be.mail.service;

import fit.iuh.cnm_project_be.auth.enums.OtpType;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MailService {
    private static final RestClient REST_CLIENT = RestClient.builder().build();

    @Value("${app.mail.from-address:}")
    String fromAddress;

    @Value("${app.mail.from-name:Zalo}")
    String fromName;

    @Value("${app.mail.brevo.api-key:${BREVO_KEY:}}")
    String brevoApiKey;

    @Value("${app.mail.brevo.send-email-api:${BREVO_SEND_EMAIL_API:}}")
    String brevoSendEmailApi;

    public void sendTextMail(String to, String subject, String content) {
        sendBrevoMail(to, subject, content, false);
    }

    public void sendOtpMail(String to, String otp, long expiryMinutes, OtpType type) {
        String subject;
        String actionName;

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

        String htmlContent = String.format(
                "<div style='font-family: Helvetica, Arial, sans-serif; max-width: 500px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;'>"
                        + "<h2 style='color: #0068ff; text-align: center;'>Zalo Verification</h2>"
                        + "<p>Chào bạn,</p>"
                        + "<p>Bạn đang thực hiện yêu cầu: <b style='color: #333;'>%s</b>.</p>"
                        + "<div style='background: #f0f7ff; border: 1px solid #0068ff; color: #0068ff; font-size: 32px; font-weight: bold; text-align: center; padding: 15px; margin: 20px 0; letter-spacing: 5px;'>%s</div>"
                        + "<p>Mã OTP này có hiệu lực trong <b>%d phút</b>.</p>"
                        + "<hr style='border: none; border-top: 1px solid #eee;' />"
                        + "<p style='font-size: 12px; color: #888;'>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email hoặc liên hệ bộ phận hỗ trợ Zalo.</p>"
                        + "<p style='font-size: 12px; color: #888; text-align: center;'>&copy; 2026 Zalo. All rights reserved.</p>"
                        + "</div>",
                actionName, otp, expiryMinutes
        );

        sendBrevoMail(to, subject, htmlContent, true);
    }

    private void sendBrevoMail(String to, String subject, String content, boolean isHtml) {
        if (to == null || to.isBlank()) {
            throw new BusinessException("Recipient email cannot be empty");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new BusinessException("Mail sender is not configured");
        }
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new BusinessException("Brevo API key is not configured");
        }
        if (brevoSendEmailApi == null || brevoSendEmailApi.isBlank()) {
            throw new BusinessException("Brevo send email API URL is not configured");
        }

        Map<String, Object> sender = Map.of(
                "name", (fromName == null || fromName.isBlank()) ? "Zalo" : fromName.trim(),
                "email", fromAddress.trim()
        );

        Map<String, Object> recipient = Map.of("email", to.trim());

        Map<String, Object> payload = Map.of(
                "sender", sender,
                "to", List.of(recipient),
                "subject", subject == null ? "" : subject.trim(),
                isHtml ? "htmlContent" : "textContent", content == null ? "" : content
        );

        try {
            REST_CLIENT.post()
                    .uri(brevoSendEmailApi.trim())
                    .header("api-key", brevoApiKey.trim())
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Send mail via Brevo failed to {}: {}", to, ex.getMessage(), ex);
            throw new BusinessException("Send mail failed");
        }
    }
}
