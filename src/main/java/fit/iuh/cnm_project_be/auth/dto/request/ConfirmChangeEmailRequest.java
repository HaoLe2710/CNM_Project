package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmChangeEmailRequest {
    @NotBlank(message = "New email is required")
    @Email(message = "New email is invalid")
    private String newEmail;

    @NotBlank(message = "Change token is required")
    private String changeToken;
}
