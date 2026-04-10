package fit.iuh.cnm_project_be.auth.dto.response;

import fit.iuh.cnm_project_be.user.enums.Platform;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserDeviceResponseDto {
    Long id;
    String deviceId;
    Platform platform;
    String deviceName;
    Instant lastSeenAt;
    Instant createdAt;
}
