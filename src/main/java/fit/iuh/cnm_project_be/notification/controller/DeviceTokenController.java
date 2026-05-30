package fit.iuh.cnm_project_be.notification.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.notification.dto.DeviceTokenResponse;
import fit.iuh.cnm_project_be.notification.dto.RegisterDeviceTokenRequest;
import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import fit.iuh.cnm_project_be.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    public ApiResponse<DeviceTokenResponse> registerDeviceToken(
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        return ApiResponse.ok(
                deviceTokenService.registerOrUpdateCurrentUserToken(request),
                UUID.randomUUID().toString()
        );
    }

    @GetMapping("/me")
    public ApiResponse<List<DeviceTokenResponse>> getMyDeviceTokens() {
        return ApiResponse.ok(deviceTokenService.getCurrentUserTokens(), UUID.randomUUID().toString());
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> revokeDeviceToken(
            @PathVariable String deviceId,
            @RequestParam(required = false) DevicePlatform platform) {
        deviceTokenService.revokeCurrentUserDeviceToken(deviceId, platform);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }
}
