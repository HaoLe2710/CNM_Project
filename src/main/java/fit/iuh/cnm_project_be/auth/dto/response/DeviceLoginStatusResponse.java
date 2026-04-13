package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceLoginStatusResponse {
    String approvalId;
    String status;
    String deviceName;
    String platform;
    String requestedByUserId;
    Long expiresAt;
}
