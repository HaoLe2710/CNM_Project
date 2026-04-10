package fit.iuh.cnm_project_be.auth.websocket;

import fit.iuh.cnm_project_be.auth.dto.request.LogoutDeviceRequest;
import fit.iuh.cnm_project_be.auth.dto.response.UserDeviceResponseDto;
import fit.iuh.cnm_project_be.auth.service.AuthService;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/**
 * WebSocket Controller cho Device Authorization
 * Handles realtime device logout notifications
 * 
 * Endpoints:
 * - /app/auth/logout-device: Logout device realtime
 * - /topic/auth/{userId}/device-logout: Topic nhận notification logout
 */
@Controller
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class DeviceAuthWebSocketController {

    AuthService authService;
    SimpMessagingTemplate messagingTemplate;

    /**
     * WebSocket: Logout device realtime
     * Message routing: /app/auth/logout-device
     * Publish to: /topic/auth/{userId}/device-logout
     */
    @MessageMapping("/auth/logout-device")
    public void logoutDeviceRealtime(
            @Payload LogoutDeviceRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            // 1. Extract userId from JWT token
            UUID userId = getCurrentUserIdFromHeaders(headerAccessor);
            
            log.info("[WebSocket] - Device logout request - userId: {}, deviceId: {}, platform: {}", 
                     userId, request.getDeviceId(), request.getPlatform());

            // 2. Execute logout
            authService.logoutDevice(userId, request.getDeviceId(), request.getPlatform());

            // 3. Send notification to client
            String topicName = "/topic/auth/" + userId + "/device-logout";
            DeviceLogoutMessage response = DeviceLogoutMessage.builder()
                    .deviceId(request.getDeviceId())
                    .platform(request.getPlatform())
                    .message("Device logged out successfully")
                    .timestamp(System.currentTimeMillis())
                    .build();

            messagingTemplate.convertAndSend(topicName, response);
            log.info("[WebSocket] - Logout notification sent to {}", topicName);

        } catch (Exception e) {
            log.error("[WebSocket] - Error during device logout: {}", e.getMessage(), e);
            String errorTopic = "/topic/auth/error";
            messagingTemplate.convertAndSend(errorTopic, buildErrorMessage(e.getMessage()));
        }
    }

    /**
     * WebSocket: Lấy danh sách devices hiện tại
     * Message routing: /app/auth/get-devices
     * Publish to: /topic/auth/{userId}/devices
     */
    @MessageMapping("/auth/get-devices")
    public void getDevicesRealtime(SimpMessageHeaderAccessor headerAccessor) {
        try {
            UUID userId = getCurrentUserIdFromHeaders(headerAccessor);
            log.info("[WebSocket] - Get devices request for userId: {}", userId);

            // Get devices list
            List<UserDeviceResponseDto> devices = authService.getDevices(userId);

            // Send to topic
            String topicName = "/topic/auth/" + userId + "/devices";
            DevicesMessage response = DevicesMessage.builder()
                    .devices(devices)
                    .timestamp(System.currentTimeMillis())
                    .build();

            messagingTemplate.convertAndSend(topicName, response);
            log.info("[WebSocket] - Devices list sent to {} ({} devices)", topicName, devices.size());

        } catch (Exception e) {
            log.error("[WebSocket] - Error retrieving devices: {}", e.getMessage(), e);
            String errorTopic = "/topic/auth/error";
            messagingTemplate.convertAndSend(errorTopic, buildErrorMessage(e.getMessage()));
        }
    }

    /**
     * Utility: Extract userId từ WebSocket headers
     */
    private UUID getCurrentUserIdFromHeaders(SimpMessageHeaderAccessor headerAccessor) {
        // Try to get from SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String userIdStr = jwt.getClaim("userId");
            if (userIdStr != null) {
                try {
                    return UUID.fromString(userIdStr);
                } catch (IllegalArgumentException e) {
                    log.error("[WebSocket] - Invalid userId format in token: {}", userIdStr);
                }
            }
        }

        throw new BusinessException("Unauthorized: User ID not found in WebSocket token");
    }

    /**
     * Build error message
     */
    private ErrorMessage buildErrorMessage(String message) {
        return ErrorMessage.builder()
                .error(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // DTO Classes
    @lombok.Data
    @lombok.Builder
    public static class DeviceLogoutMessage {
        String deviceId;
        String platform;
        String message;
        long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class DevicesMessage {
        List<UserDeviceResponseDto> devices;
        long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class ErrorMessage {
        String error;
        long timestamp;
    }
}
