package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZaloLockSettingsResponse {
    private boolean enabled;
    private String method;
    private boolean pinConfigured;
    private boolean biometricEnabled;
}
