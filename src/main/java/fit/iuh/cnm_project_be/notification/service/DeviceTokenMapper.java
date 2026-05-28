package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.DeviceTokenResponse;
import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import org.springframework.stereotype.Component;

@Component
public class DeviceTokenMapper {

    public DeviceTokenResponse toResponse(DeviceToken token) {
        return DeviceTokenResponse.builder()
                .id(token.getId())
                .userId(token.getUserId())
                .deviceId(token.getDeviceId())
                .platform(token.getPlatform())
                .provider(token.getProvider())
                .maskedToken(maskToken(token.getToken()))
                .enabled(token.isEnabled())
                .lastSeenAt(token.getLastSeenAt())
                .lastFailedAt(token.getLastFailedAt())
                .revokedAt(token.getRevokedAt())
                .createdAt(token.getCreatedAt())
                .updatedAt(token.getUpdatedAt())
                .build();
    }

    public String maskToken(String token) {
        if (token == null || token.length() <= 16) {
            return "***";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 6);
    }
}
