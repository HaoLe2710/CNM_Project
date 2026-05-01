package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.request.DeviceLoginApprovalRequest;
import fit.iuh.cnm_project_be.auth.dto.request.DeviceLoginRequest;
import fit.iuh.cnm_project_be.auth.dto.request.LoginRequest;
import fit.iuh.cnm_project_be.auth.dto.response.DeviceLoginQrResponse;
import fit.iuh.cnm_project_be.auth.dto.response.DeviceLoginStatusResponse;
import fit.iuh.cnm_project_be.auth.dto.response.LoginResponse;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.auth.utils.JwtUtils;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.user.entity.UserDevice;
import fit.iuh.cnm_project_be.user.enums.Platform;
import fit.iuh.cnm_project_be.user.service.UserDeviceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class DeviceLoginService {
    static String REQUEST_KEY_PREFIX = "auth:device-login:request:";
    static long REQUEST_TTL_SECONDS = 180;
    static long ACCESS_TOKEN_EXPIRE_MINUTES = 15;
    static int MAX_ACTIVE_DEVICES = 2;

    StringRedisTemplate redisTemplate;
    AccountRepository accountRepository;
    UserDeviceService userDeviceService;
    JwtUtils jwtUtils;
    TokenCookieService tokenCookieService;
    TokenRedisService tokenRedisService;
    SimpMessagingTemplate messagingTemplate;

    public DeviceLoginQrResponse createRequest(DeviceLoginRequest request) {
        DeviceLoginSession session = buildSession(
                request.getDeviceId().trim(),
                request.getPlatform().name(),
                resolveDeviceName(request.getDeviceName(), "Zalo Web"),
                null
        );
        saveSession(session, REQUEST_TTL_SECONDS);

        return DeviceLoginQrResponse.builder()
                .approvalId(session.getApprovalId())
                .qrContent("{\"type\":\"DEVICE_LOGIN\",\"requestId\":\"" + session.getApprovalId() + "\"}")
                .status(session.getStatus())
                .expiresInSeconds(REQUEST_TTL_SECONDS)
                .deviceName(session.getDeviceName())
                .platform(session.getPlatform())
                .build();
    }

    public LoginResponse createCredentialApprovalRequest(Account account, LoginRequest request) {
        DeviceLoginSession session = buildSession(
                request.getDeviceId().trim(),
                request.getPlatform().name(),
                resolveDeviceName(request.getDeviceName(), "Unknown device"),
                account.getUserId().toString()
        );
        saveSession(session, REQUEST_TTL_SECONDS);
        publishApprovalRequest(session);

        return LoginResponse.builder()
                .email(account.getEmail())
                .phone(account.getPhone())
                .userId(account.getUserId())
                .roles(account.getRoles())
                .status("PENDING_APPROVAL")
                .approvalId(session.getApprovalId())
                .deviceName(session.getDeviceName())
                .platform(session.getPlatform())
                .build();
    }

    public boolean shouldRequireApproval(UUID userId, String deviceId, Platform platform) {
        if (userDeviceService.isDeviceValid(userId, deviceId, platform)) {
            return false;
        }

        return !userDeviceService.getUserDeviceByUserId(userId).isEmpty();
    }

    public DeviceLoginStatusResponse getStatus(String approvalId, HttpServletResponse response) {
        DeviceLoginSession session = getSession(approvalId)
                .orElse(DeviceLoginSession.builder()
                        .approvalId(approvalId)
                        .status("EXPIRED")
                        .build());

        if ("APPROVED".equalsIgnoreCase(session.getStatus())
                && session.getApprovedUserId() != null
                && session.getFinalizedAt() == null) {
            finalizeApprovedLogin(session, response);
            session.setStatus("COMPLETED");
            session.setFinalizedAt(String.valueOf(System.currentTimeMillis()));

            long ttl = Optional.ofNullable(redisTemplate.getExpire(buildKey(approvalId), TimeUnit.SECONDS))
                    .filter(value -> value > 0)
                    .orElse(30L);
            saveSession(session, ttl);
        }

        return toStatusResponse(session);
    }

    public DeviceLoginStatusResponse approve(UUID approverUserId, DeviceLoginApprovalRequest request) {
        DeviceLoginSession session = getSession(request.getRequestId())
                .orElseThrow(() -> new BusinessException("QR login request has expired"));

        String normalizedStatus = request.getStatus().trim().toUpperCase();
        if (!"APPROVED".equals(normalizedStatus) && !"REJECTED".equals(normalizedStatus)) {
            throw new BusinessException("Unsupported approval status");
        }

        if (session.getRequestedByUserId() != null
                && !approverUserId.toString().equals(session.getRequestedByUserId())) {
            throw new BusinessException("Approval request does not belong to current user");
        }

        if (!"PENDING".equalsIgnoreCase(session.getStatus())) {
            return toStatusResponse(session);
        }

        session.setStatus(normalizedStatus);
        session.setApprovedUserId("APPROVED".equals(normalizedStatus) ? approverUserId.toString() : null);
        session.setApprovedAt(String.valueOf(System.currentTimeMillis()));

        long ttl = Optional.ofNullable(redisTemplate.getExpire(buildKey(session.getApprovalId()), TimeUnit.SECONDS))
                .filter(value -> value > 0)
                .orElse(60L);
        saveSession(session, ttl);

        return toStatusResponse(session);
    }

    private DeviceLoginSession buildSession(
            String deviceId,
            String platform,
            String deviceName,
            String requestedByUserId
    ) {
        return DeviceLoginSession.builder()
                .approvalId(UUID.randomUUID().toString())
                .deviceId(deviceId)
                .platform(platform)
                .deviceName(deviceName)
                .status("PENDING")
                .requestedByUserId(requestedByUserId)
                .expiresAt(Instant.now().plusSeconds(REQUEST_TTL_SECONDS).toEpochMilli())
                .build();
    }

    private void finalizeApprovedLogin(DeviceLoginSession session, HttpServletResponse response) {
        UUID userId = UUID.fromString(session.getApprovedUserId());
        Account account = accountRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("Approved account no longer exists"));

        Platform platform = Platform.valueOf(session.getPlatform());
        String platformValue = session.getPlatform().toLowerCase();

        userDeviceService.saveOrUpdateDevice(
                account.getUserId(),
                session.getDeviceId(),
                platform,
                session.getDeviceName()
        );

        evictOverflowDevices(account.getUserId(), session.getDeviceId(), session.getPlatform());

        String accessToken = jwtUtils.generateToken(account, session.getDeviceId(), platformValue);
        String refreshToken = jwtUtils.generateRefreshToken();

        tokenRedisService.saveRefreshToken(account.getUserId(), platformValue, refreshToken);
        tokenCookieService.setTokenToCookie(
                response,
                "accessToken",
                accessToken,
                Duration.ofMinutes(ACCESS_TOKEN_EXPIRE_MINUTES)
        );
        tokenCookieService.setTokenToCookie(response, "refreshToken", refreshToken, Duration.ofDays(30));

        log.info("[DeviceLogin] - Approved login finalized for userId {} on {}", account.getUserId(), session.getPlatform());
    }

    private void evictOverflowDevices(UUID userId, String currentDeviceId, String currentPlatform) {
        List<UserDevice> devices = sortDevices(userDeviceService.getUserDeviceByUserId(userId));

        while (devices.size() > MAX_ACTIVE_DEVICES) {
            UserDevice deviceToRemove = devices.stream()
                    .filter(device -> !(device.getDeviceId().equals(currentDeviceId)
                            && device.getPlatform().name().equalsIgnoreCase(currentPlatform)))
                    .findFirst()
                    .orElse(null);

            if (deviceToRemove == null) {
                return;
            }

            userDeviceService.deleteDevice(
                    userId,
                    deviceToRemove.getDeviceId(),
                    deviceToRemove.getPlatform().name()
            );
            publishDeviceLogout(userId, deviceToRemove);
            devices = sortDevices(userDeviceService.getUserDeviceByUserId(userId));
        }
    }

    private List<UserDevice> sortDevices(List<UserDevice> devices) {
        return devices.stream()
                .sorted(Comparator.comparing(
                        UserDevice::getLastSeenAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private void publishApprovalRequest(DeviceLoginSession session) {
        if (session.getRequestedByUserId() == null) {
            return;
        }

        String topicName = "/topic/auth/" + session.getRequestedByUserId() + "/device-login-request";
        messagingTemplate.convertAndSend(topicName, (Object) toStatusResponse(session));
    }

    private void publishDeviceLogout(UUID userId, UserDevice device) {
        String topicName = "/topic/auth/" + userId + "/device-logout";
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", device.getDeviceId());
        payload.put("platform", device.getPlatform().name());
        payload.put("message", "Device logged out due to device limit");
        payload.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend(topicName, (Object) payload);
    }

    private DeviceLoginStatusResponse toStatusResponse(DeviceLoginSession session) {
        return DeviceLoginStatusResponse.builder()
                .approvalId(session.getApprovalId())
                .status(session.getStatus())
                .deviceName(session.getDeviceName())
                .platform(session.getPlatform())
                .requestedByUserId(session.getRequestedByUserId())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    private Optional<DeviceLoginSession> getSession(String approvalId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(buildKey(approvalId));
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(DeviceLoginSession.builder()
                .approvalId(asString(entries.get("approvalId")))
                .deviceId(asString(entries.get("deviceId")))
                .platform(asString(entries.get("platform")))
                .deviceName(asString(entries.get("deviceName")))
                .status(asString(entries.get("status")))
                .requestedByUserId(asString(entries.get("requestedByUserId")))
                .approvedUserId(asString(entries.get("approvedUserId")))
                .approvedAt(asString(entries.get("approvedAt")))
                .finalizedAt(asString(entries.get("finalizedAt")))
                .expiresAt(parseLong(entries.get("expiresAt")))
                .build());
    }

    private void saveSession(DeviceLoginSession session, long ttlSeconds) {
        Map<String, String> values = new HashMap<>();
        values.put("approvalId", session.getApprovalId());
        values.put("deviceId", session.getDeviceId());
        values.put("platform", session.getPlatform());
        values.put("deviceName", session.getDeviceName());
        values.put("status", session.getStatus());
        values.put("expiresAt", String.valueOf(session.getExpiresAt()));

        if (session.getRequestedByUserId() != null) {
            values.put("requestedByUserId", session.getRequestedByUserId());
        }
        if (session.getApprovedUserId() != null) {
            values.put("approvedUserId", session.getApprovedUserId());
        }
        if (session.getApprovedAt() != null) {
            values.put("approvedAt", session.getApprovedAt());
        }
        if (session.getFinalizedAt() != null) {
            values.put("finalizedAt", session.getFinalizedAt());
        }

        String key = buildKey(session.getApprovalId());
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    private String buildKey(String approvalId) {
        return REQUEST_KEY_PREFIX + approvalId;
    }

    private String resolveDeviceName(String deviceName, String fallback) {
        if (deviceName == null || deviceName.isBlank()) {
            return fallback;
        }
        return deviceName.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    static class DeviceLoginSession {
        String approvalId;
        String deviceId;
        String platform;
        String deviceName;
        String status;
        String requestedByUserId;
        String approvedUserId;
        String approvedAt;
        String finalizedAt;
        Long expiresAt;
    }
}
