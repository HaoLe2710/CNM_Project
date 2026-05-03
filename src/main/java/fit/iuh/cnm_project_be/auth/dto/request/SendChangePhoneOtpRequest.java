package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendChangePhoneOtpRequest {
    @NotBlank(message = "New phone is required")
    private String newPhone;
}
