package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.DeviceTokenResponse;
import fit.iuh.cnm_project_be.notification.dto.RegisterDeviceTokenRequest;
import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
import fit.iuh.cnm_project_be.notification.repository.DeviceTokenRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private UserService userService;

    private DeviceTokenService service;
    private final List<DeviceToken> savedTokens = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DeviceTokenService(deviceTokenRepository, new DeviceTokenMapper(), userService);
        lenient().when(deviceTokenRepository.save(any(DeviceToken.class))).thenAnswer(invocation -> {
            DeviceToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
                token.setCreatedAt(Instant.now());
            }
            token.setUpdatedAt(Instant.now());
            savedTokens.removeIf(existing -> existing.getId().equals(token.getId()));
            savedTokens.add(token);
            return token;
        });
    }

    @Test
    void registerNewTokenSuccess() {
        UUID userId = UUID.randomUUID();
        RegisterDeviceTokenRequest request = request("device-1", DevicePlatform.ANDROID, "1234567890abcdef-token");
        when(deviceTokenRepository.findByUserIdAndDeviceIdAndPlatform(userId, "device-1", DevicePlatform.ANDROID))
                .thenReturn(Optional.empty());

        DeviceToken token = service.registerOrUpdateToken(userId, request);

        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getDeviceId()).isEqualTo("device-1");
        assertThat(token.getProvider()).isEqualTo(PushProvider.FCM);
        assertThat(token.isEnabled()).isTrue();
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void registerSameDeviceUpdatesExistingToken() {
        UUID userId = UUID.randomUUID();
        DeviceToken existing = token(userId, "device-1", DevicePlatform.ANDROID, "old-token");
        when(deviceTokenRepository.findByUserIdAndDeviceIdAndPlatform(userId, "device-1", DevicePlatform.ANDROID))
                .thenReturn(Optional.of(existing));

        DeviceToken updated = service.registerOrUpdateToken(
                userId,
                request("device-1", DevicePlatform.ANDROID, "new-token-value")
        );

        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getToken()).isEqualTo("new-token-value");
    }

    @Test
    void registerSameDeviceReEnablesRevokedToken() {
        UUID userId = UUID.randomUUID();
        DeviceToken existing = token(userId, "device-1", DevicePlatform.ANDROID, "old-token");
        existing.setEnabled(false);
        existing.setRevokedAt(Instant.now());
        when(deviceTokenRepository.findByUserIdAndDeviceIdAndPlatform(userId, "device-1", DevicePlatform.ANDROID))
                .thenReturn(Optional.of(existing));

        DeviceToken updated = service.registerOrUpdateToken(
                userId,
                request("device-1", DevicePlatform.ANDROID, "new-token-value")
        );

        assertThat(updated.isEnabled()).isTrue();
        assertThat(updated.getRevokedAt()).isNull();
    }

    @Test
    void registerDifferentDeviceCreatesSecondToken() {
        UUID userId = UUID.randomUUID();
        when(deviceTokenRepository.findByUserIdAndDeviceIdAndPlatform(any(), any(), any()))
                .thenReturn(Optional.empty());

        service.registerOrUpdateToken(userId, request("device-1", DevicePlatform.ANDROID, "token-one"));
        service.registerOrUpdateToken(userId, request("device-2", DevicePlatform.ANDROID, "token-two"));

        assertThat(savedTokens).hasSize(2);
    }

    @Test
    void revokeCurrentUserDeviceTokenSuccess() {
        UUID userId = UUID.randomUUID();
        DeviceToken existing = token(userId, "device-1", DevicePlatform.ANDROID, "token-one");
        when(deviceTokenRepository.findByUserIdAndDeviceId(userId, "device-1"))
                .thenReturn(List.of(existing));

        service.revokeDeviceToken(userId, "device-1", (DevicePlatform) null);

        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeCurrentUserDeviceTokenIdempotent() {
        UUID userId = UUID.randomUUID();
        Instant revokedAt = Instant.parse("2026-05-28T00:00:00Z");
        DeviceToken existing = token(userId, "device-1", DevicePlatform.ANDROID, "token-one");
        existing.setEnabled(false);
        existing.setRevokedAt(revokedAt);
        when(deviceTokenRepository.findByUserIdAndDeviceId(userId, "device-1"))
                .thenReturn(List.of(existing));

        service.revokeDeviceToken(userId, "device-1", (DevicePlatform) null);

        assertThat(existing.getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void revokeDoesNotAffectOtherUserToken() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        DeviceToken other = token(otherUserId, "device-1", DevicePlatform.ANDROID, "token-one");
        when(deviceTokenRepository.findByUserIdAndDeviceId(userId, "device-1"))
                .thenReturn(List.of());

        service.revokeDeviceToken(userId, "device-1", (DevicePlatform) null);

        assertThat(other.isEnabled()).isTrue();
        assertThat(other.getRevokedAt()).isNull();
    }

    @Test
    void responseMasksToken() {
        UUID userId = UUID.randomUUID();
        DeviceToken existing = token(userId, "device-1", DevicePlatform.ANDROID, "abcdefgh1234567890");
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(deviceTokenRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(existing));

        DeviceTokenResponse response = service.getCurrentUserTokens().get(0);

        assertThat(response.getMaskedToken()).isEqualTo("abcdefgh...567890");
    }

    private RegisterDeviceTokenRequest request(String deviceId, DevicePlatform platform, String tokenValue) {
        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest();
        request.setDeviceId(deviceId);
        request.setPlatform(platform);
        request.setProvider(PushProvider.FCM);
        request.setToken(tokenValue);
        return request;
    }

    private DeviceToken token(UUID userId, String deviceId, DevicePlatform platform, String tokenValue) {
        DeviceToken token = new DeviceToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setDeviceId(deviceId);
        token.setPlatform(platform);
        token.setProvider(PushProvider.FCM);
        token.setToken(tokenValue);
        token.setEnabled(true);
        token.setCreatedAt(Instant.now());
        token.setUpdatedAt(Instant.now());
        return token;
    }
}
