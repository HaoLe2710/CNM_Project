package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmChangePhoneRequest {
    @NotBlank(message = "New phone is required")
    private String newPhone;

    @NotBlank(message = "Change token is required")
    private String changeToken;
}
