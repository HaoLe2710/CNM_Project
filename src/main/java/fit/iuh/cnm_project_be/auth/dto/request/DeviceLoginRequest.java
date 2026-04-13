package fit.iuh.cnm_project_be.auth.dto.request;

import fit.iuh.cnm_project_be.user.enums.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class DeviceLoginRequest {
    @NotBlank(message = "Device ID cannot be empty")
    String deviceId;

    @NotNull(message = "Platform cannot be empty")
    Platform platform;

    String deviceName;
}
