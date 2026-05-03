package fit.iuh.cnm_project_be.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnlockZaloLockRequest {
    @NotBlank(message = "Challenge token is required")
    private String challengeToken;

    @NotBlank(message = "PIN is required")
    private String pin;
}
