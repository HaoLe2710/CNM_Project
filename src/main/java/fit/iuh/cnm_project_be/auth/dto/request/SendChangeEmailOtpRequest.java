package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendChangeEmailOtpRequest {
    @NotBlank(message = "New email is required")
    @Email(message = "New email is invalid")
    private String newEmail;
}
