package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.request.LoginRequest;
import fit.iuh.cnm_project_be.auth.dto.request.RegisterRequest;
import fit.iuh.cnm_project_be.auth.dto.request.DeviceLoginApprovalRequest;
import fit.iuh.cnm_project_be.auth.dto.request.DeviceLoginRequest;
import fit.iuh.cnm_project_be.auth.dto.response.CheckEmailResponse;
import fit.iuh.cnm_project_be.auth.dto.response.DeviceLoginQrResponse;
import fit.iuh.cnm_project_be.auth.dto.response.DeviceLoginStatusResponse;
import fit.iuh.cnm_project_be.auth.dto.response.LoginResponse;
import fit.iuh.cnm_project_be.auth.dto.response.LogoutAllDevicesResponse;
import fit.iuh.cnm_project_be.auth.dto.response.RegisterResponse;
import fit.iuh.cnm_project_be.auth.dto.response.RefreshTokenResponse;
import fit.iuh.cnm_project_be.auth.dto.response.SecurityHistoryItemResponse;
import fit.iuh.cnm_project_be.auth.dto.response.UserDeviceResponseDto;
import fit.iuh.cnm_project_be.auth.entity.SecurityAuditLog;
import fit.iuh.cnm_project_be.notification.service.DeviceTokenService;
import fit.iuh.cnm_project_be.user.enums.Platform;
import fit.iuh.cnm_project_be.user.service.UserDeviceService;
import fit.iuh.cnm_project_be.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Facade Service - Điều phối các service con
 * Đây là entry point chính cho tất cả auth operations
 * 
 * Services:
 * - LoginService: Xử lý login logic
 * - AuthRefreshService: Xử lý refresh token logic
 * - LogoutService: Xử lý logout logic
 * - RegistrationService: Xử lý register logic
 * - AccountService: Quản lý account operations
 * - TokenCookieService: Quản lý HTTP cookies
 * - TokenRedisService: Quản lý Redis refresh tokens
 * - UserDeviceService: Quản lý thiết bị của user
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class AuthService {

    LoginService loginService;
    DeviceLoginService deviceLoginService;
    AuthRefreshService authRefreshService;
    LogoutService logoutService;
    RegistrationService registrationService;
    AccountService accountService;
    UserDeviceService userDeviceService;
    UserService userService;
    DeviceTokenService deviceTokenService;
    TokenRedisService tokenRedisService;
    SecurityAuditService securityAuditService;

    /**
     * Đăng nhập và tạo tokens (access + refresh)
     */
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        return loginService.login(request, response);
    }

    public DeviceLoginQrResponse createDeviceLoginRequest(DeviceLoginRequest request) {
        return deviceLoginService.createRequest(request);
    }

    public DeviceLoginStatusResponse getDeviceLoginStatus(String approvalId, HttpServletResponse response) {
        return deviceLoginService.getStatus(approvalId, response);
    }

    public DeviceLoginStatusResponse approveDeviceLogin(UUID userId, DeviceLoginApprovalRequest request) {
        return deviceLoginService.approve(userId, request);
    }

    /**
     * Làm mới access token bằng refresh token
     * Nếu RT hết hạn thì đăng xuất ngay lập tức
     */
    public RefreshTokenResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        return authRefreshService.refreshAccessToken(request, response);
    }

    /**
     * Đăng ký tài khoản mới
     */
    public RegisterResponse register(RegisterRequest request) {
        return registrationService.register(request);
    }

    public CheckEmailResponse checkEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        boolean existed = accountService.checkEmailExists(normalizedEmail);

        return CheckEmailResponse.builder()
                .email(normalizedEmail)
                .exists(existed)
                .nextStep(existed ? "LOGIN" : "REGISTER")
                .message(existed
                        ? "Email already exists. Please continue to login."
                        : "Email is available. Please continue with registration and email verification.")
                .build();
    }

    /**
     * Kiểm tra email/phone có tồn tại chưa
     */
    public boolean checkExisted(String identifier) {
        return accountService.checkExisted(identifier);
    }

    /**
     * Đăng xuất
     */
    public boolean logout(HttpServletRequest request, HttpServletResponse httpServletResponse) {
        return logoutService.   logout(request, httpServletResponse);
    }

    /**
     * Soft delete account theo userId
     */
    public void softDeleteAccountByUserId(java.util.UUID userId) {
        accountService.softDeleteAccountByUserId(userId);
    }

    /**
     * Soft delete cả user và account
     */
    public void softDeleteUserAndAccount(java.util.UUID userId) {
        accountService.softDeleteUserAndAccount(userId);
    }

    /**
     * Lấy danh sách tất cả devices của user
     */
    public List<UserDeviceResponseDto> getDevices(UUID userId) {
        return userDeviceService.getUserDeviceByUserId(userId)
                .stream()
                .map(device -> UserDeviceResponseDto.builder()
                        .id(device.getId())
                        .deviceId(device.getDeviceId())
                        .platform(device.getPlatform())
                        .deviceName(device.getDeviceName())
                        .lastSeenAt(device.getLastSeenAt())
                        .createdAt(device.getCreatedAt())
                        .location("Chưa xác định")
                        .loginMethod("Mật khẩu")
                        .trustedDevice(Boolean.TRUE)
                        .build())
                .collect(Collectors.toList());
    }

    org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    /**
     * Đăng xuất device cụ thể
     */
    public void logoutDevice(UUID userId, String deviceId, String platform) {
        String normalizedPlatform = platform == null ? "" : platform.trim().toLowerCase();
        String normalizedDeviceId = deviceId == null ? null : deviceId.trim();

        tokenRedisService.deleteRefreshTokensByScope(userId, normalizedPlatform, normalizedDeviceId);
        deviceTokenService.revokeDeviceToken(userId, normalizedDeviceId, platform);
        userDeviceService.deleteDevice(userId, normalizedDeviceId, platform);

        try {
            Platform resolvedPlatform = Platform.valueOf(platform.toUpperCase());
            if (resolvedPlatform == Platform.ANDROID || resolvedPlatform == Platform.IOS) {
                userService.updateFcmToken(userId, null);
            }
        } catch (IllegalArgumentException ignored) {
            // Ignore invalid platform value for FCM cleanup.
        }

        log.info("[Device Logout] - User {} logged out from device {} on platform {}", userId, normalizedDeviceId, normalizedPlatform);
        securityAuditService.log(
                userId,
                "DEVICE_LOGOUT_SINGLE",
                "Đã đăng xuất 1 thiết bị",
                "Đã đăng xuất thiết bị " + normalizedDeviceId + " trên nền tảng " + normalizedPlatform,
                normalizedDeviceId,
                normalizedPlatform
        );

        try {
            String topicName = "/topic/auth/" + userId + "/device-logout";
            fit.iuh.cnm_project_be.auth.websocket.DeviceAuthWebSocketController.DeviceLogoutMessage response = 
                    fit.iuh.cnm_project_be.auth.websocket.DeviceAuthWebSocketController.DeviceLogoutMessage.builder()
                    .deviceId(normalizedDeviceId)
                    .platform(normalizedPlatform)
                    .message("Đã đăng xuất thiết bị thành công")
                    .timestamp(System.currentTimeMillis())
                    .build();
            messagingTemplate.convertAndSend(topicName, response);
            log.info("[WebSocket] - Logout notification sent to {} for device {}", topicName, normalizedDeviceId);
        } catch (Exception e) {
            log.error("[WebSocket] - Error sending logout notification: {}", e.getMessage(), e);
        }
    }

    public LogoutAllDevicesResponse logoutAllDevices(UUID userId) {
        int deviceCount = userDeviceService.getUserDeviceByUserId(userId).size();
        tokenRedisService.deleteRefreshTokensByScope(userId, "web");
        tokenRedisService.deleteRefreshTokensByScope(userId, "android");
        tokenRedisService.deleteRefreshTokensByScope(userId, "ios");
        userDeviceService.deleteAllDevices(userId);
        userService.updateFcmToken(userId, null);

        securityAuditService.log(
                userId,
                "DEVICE_LOGOUT_ALL",
                "Đã đăng xuất tất cả thiết bị",
                "Tất cả các phiên đăng nhập đã được thu hồi ở cấp refresh token và danh sách thiết bị",
                null,
                null
        );

        return LogoutAllDevicesResponse.builder()
                .loggedOutDeviceCount(deviceCount)
                .currentSessionMayRemainUntilExpiry(true)
                .message("Đã đăng xuất tất cả thiết bị đã ghi nhớ. Access token hiện tại có thể còn hiệu lực cho đến khi hết hạn.")
                .build();
    }

    public List<SecurityHistoryItemResponse> getLogoutHistory(UUID userId, Integer size) {
        int resolvedSize = size == null || size <= 0 ? 50 : Math.min(size, 200);
        return securityAuditService.getLogoutHistory(userId, resolvedSize).stream()
                .map(this::toSecurityHistoryItem)
                .toList();
    }

    private SecurityHistoryItemResponse toSecurityHistoryItem(SecurityAuditLog log) {
        return SecurityHistoryItemResponse.builder()
                .id(log.getId())
                .eventType(log.getEventType())
                .title(log.getTitle())
                .detail(log.getDetail())
                .deviceId(log.getDeviceId())
                .platform(log.getPlatform())
                .createdAt(log.getCreatedAt())
                .build();
    }
}

