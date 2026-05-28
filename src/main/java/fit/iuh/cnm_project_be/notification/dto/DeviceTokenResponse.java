package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DeviceTokenResponse {
    private UUID id;
    private UUID userId;
    private String deviceId;
    private DevicePlatform platform;
    private PushProvider provider;
    private String maskedToken;
    private boolean enabled;
    private Instant lastSeenAt;
    private Instant lastFailedAt;
    private Instant revokedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
