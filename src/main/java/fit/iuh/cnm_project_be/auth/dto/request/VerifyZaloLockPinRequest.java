package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyZaloLockPinRequest {
    @NotBlank(message = "PIN is required")
    private String pin;
}
