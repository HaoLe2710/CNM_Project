package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyChangeEmailOtpRequest {
    @NotBlank(message = "New email is required")
    @Email(message = "New email is invalid")
    private String newEmail;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits")
    private String otp;
}
