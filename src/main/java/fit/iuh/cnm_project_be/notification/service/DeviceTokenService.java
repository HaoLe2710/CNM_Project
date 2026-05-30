package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.notification.dto.DeviceTokenResponse;
import fit.iuh.cnm_project_be.notification.dto.RegisterDeviceTokenRequest;
import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
import fit.iuh.cnm_project_be.notification.repository.DeviceTokenRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenService {

    public static final String LEGACY_DEVICE_ID = "legacy-mobile";

    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenMapper deviceTokenMapper;
    private final UserService userService;

    @Transactional
    public DeviceTokenResponse registerOrUpdateCurrentUserToken(RegisterDeviceTokenRequest request) {
        return deviceTokenMapper.toResponse(registerOrUpdateToken(userService.getCurrentUserId(), request));
    }

    @Transactional
    public DeviceToken registerOrUpdateToken(UUID userId, RegisterDeviceTokenRequest request) {
        validate(userId, request);
        Instant now = Instant.now();
        DevicePlatform platform = request.getPlatform();
        PushProvider provider = request.getProvider() == null ? PushProvider.FCM : request.getProvider();
        String deviceId = request.getDeviceId().trim();
        String token = request.getToken().trim();

        DeviceToken deviceToken = deviceTokenRepository.findByUserIdAndDeviceIdAndPlatform(userId, deviceId, platform)
                .orElseGet(DeviceToken::new);
        deviceToken.setUserId(userId);
        deviceToken.setDeviceId(deviceId);
        deviceToken.setPlatform(platform);
        deviceToken.setProvider(provider);
        deviceToken.setToken(token);
        deviceToken.setEnabled(true);
        deviceToken.setRevokedAt(null);
        deviceToken.setLastSeenAt(now);
        return deviceTokenRepository.save(deviceToken);
    }

    @Transactional
    public void registerLegacyFcmToken(UUID userId, String rawToken, String deviceId, String platform) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest();
        request.setDeviceId(resolveLegacyDeviceId(deviceId));
        request.setPlatform(resolveLegacyPlatform(platform));
        request.setProvider(PushProvider.FCM);
        request.setToken(rawToken);
        registerOrUpdateToken(userId, request);
    }

    @Transactional
    public void revokeCurrentUserDeviceToken(String deviceId, DevicePlatform platform) {
        revokeDeviceToken(userService.getCurrentUserId(), deviceId, platform);
    }

    @Transactional
    public void revokeDeviceToken(UUID userId, String deviceId, DevicePlatform platform) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        List<DeviceToken> tokens = platform == null
                ? deviceTokenRepository.findByUserIdAndDeviceId(userId, deviceId.trim())
                : deviceTokenRepository.findByUserIdAndDeviceIdAndPlatform(userId, deviceId.trim(), platform)
                .stream()
                .toList();
        tokens.forEach(token -> {
            token.setEnabled(false);
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
            }
        });
        deviceTokenRepository.saveAll(tokens);
    }

    @Transactional
    public void revokeDeviceToken(UUID userId, String deviceId, String platform) {
        revokeDeviceToken(userId, deviceId, parsePlatformOrNull(platform));
    }

    @Transactional
    public void disableTokenAfterFailure(UUID tokenId, String reason) {
        deviceTokenRepository.findById(tokenId).ifPresent(token -> {
            Instant now = Instant.now();
            token.setEnabled(false);
            token.setLastFailedAt(now);
            token.setRevokedAt(now);
            deviceTokenRepository.save(token);
            log.warn(
                    "Disabled push token id={} userId={} deviceId={} platform={} provider={} maskedToken={} reason={}",
                    token.getId(),
                    token.getUserId(),
                    token.getDeviceId(),
                    token.getPlatform(),
                    token.getProvider(),
                    deviceTokenMapper.maskToken(token.getToken()),
                    reason
            );
        });
    }

    @Transactional(readOnly = true)
    public List<DeviceToken> findActiveTokensByUserId(UUID userId) {
        return deviceTokenRepository.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<DeviceToken> findActiveTokensByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return deviceTokenRepository.findActiveByUserIds(userIds);
    }

    @Transactional(readOnly = true)
    public List<DeviceTokenResponse> getCurrentUserTokens() {
        return deviceTokenRepository.findByUserIdOrderByCreatedAtDesc(userService.getCurrentUserId())
                .stream()
                .map(deviceTokenMapper::toResponse)
                .toList();
    }

    private void validate(UUID userId, RegisterDeviceTokenRequest request) {
        if (userId == null) {
            throw new BusinessException("User id is required");
        }
        if (request == null) {
            throw new BusinessException("Device token request is required");
        }
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            throw new BusinessException("Device id is required");
        }
        if (request.getDeviceId().length() > 255) {
            throw new BusinessException("Device id is too long");
        }
        if (request.getPlatform() == null) {
            throw new BusinessException("Device platform is required");
        }
        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new BusinessException("Push token is required");
        }
        if (request.getToken().length() > 4096) {
            throw new BusinessException("Push token is too long");
        }
    }

    private String resolveLegacyDeviceId(String deviceId) {
        return deviceId == null || deviceId.isBlank() ? LEGACY_DEVICE_ID : deviceId.trim();
    }

    private DevicePlatform resolveLegacyPlatform(String platform) {
        DevicePlatform parsed = parsePlatformOrNull(platform);
        return parsed == null ? DevicePlatform.ANDROID : parsed;
    }

    private DevicePlatform parsePlatformOrNull(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        try {
            return DevicePlatform.valueOf(platform.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
