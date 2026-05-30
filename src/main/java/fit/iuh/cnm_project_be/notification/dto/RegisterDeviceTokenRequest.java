package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDeviceTokenRequest {

    @NotBlank(message = "Device id is required")
    @Size(max = 255, message = "Device id is too long")
    private String deviceId;

    @NotNull(message = "Platform is required")
    private DevicePlatform platform;

    private PushProvider provider;

    @NotBlank(message = "Push token is required")
    @Size(max = 4096, message = "Push token is too long")
    private String token;
}
