package fit.iuh.cnm_project_be.auth.dto.request;

import lombok.Data;

@Data
public class UpdateZaloLockRequest {
    private Boolean enabled;
    private String method;
    private String pin;
    private Boolean biometricEnabled;
}
