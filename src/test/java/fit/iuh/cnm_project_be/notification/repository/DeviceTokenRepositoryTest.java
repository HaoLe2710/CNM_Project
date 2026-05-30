package fit.iuh.cnm_project_be.notification.repository;

import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import fit.iuh.cnm_project_be.notification.enums.DevicePlatform;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.docker.compose.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class DeviceTokenRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                create table if not exists device_tokens (
                    id uuid primary key,
                    user_id uuid not null,
                    device_id varchar(255) not null,
                    platform varchar(30) not null,
                    provider varchar(30) not null,
                    token text not null,
                    enabled boolean not null,
                    last_seen_at timestamp with time zone,
                    last_failed_at timestamp with time zone,
                    revoked_at timestamp with time zone,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null,
                    constraint uk_device_tokens_user_device_platform unique (user_id, device_id, platform)
                )
                """);
        jdbcTemplate.execute("delete from device_tokens");
    }

    @Test
    void uniqueUserDevicePlatformEnforced() {
        UUID userId = UUID.randomUUID();
        save(userId, "device-1", DevicePlatform.ANDROID, "token-1", true, null);

        DeviceToken duplicate = build(userId, "device-1", DevicePlatform.ANDROID, "token-2", true, null);

        assertThatThrownBy(() -> deviceTokenRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findActiveByUserIdReturnsOnlyEnabledNonRevoked() {
        UUID userId = UUID.randomUUID();
        DeviceToken active = save(userId, "active", DevicePlatform.ANDROID, "token-active", true, null);
        save(userId, "disabled", DevicePlatform.IOS, "token-disabled", false, null);
        save(userId, "revoked", DevicePlatform.WEB, "token-revoked", true, Instant.now());

        List<DeviceToken> tokens = deviceTokenRepository.findActiveByUserId(userId);

        assertThat(tokens).extracting(DeviceToken::getId).containsExactly(active.getId());
    }

    @Test
    void findActiveByUserIdsReturnsExpectedTokens() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID thirdUserId = UUID.randomUUID();
        DeviceToken first = save(firstUserId, "first", DevicePlatform.ANDROID, "token-first", true, null);
        DeviceToken second = save(secondUserId, "second", DevicePlatform.IOS, "token-second", true, null);
        save(thirdUserId, "third", DevicePlatform.WEB, "token-third", true, null);

        List<DeviceToken> tokens = deviceTokenRepository.findActiveByUserIds(List.of(firstUserId, secondUserId));

        assertThat(tokens).extracting(DeviceToken::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    private DeviceToken save(
            UUID userId,
            String deviceId,
            DevicePlatform platform,
            String tokenValue,
            boolean enabled,
            Instant revokedAt) {
        return deviceTokenRepository.saveAndFlush(build(userId, deviceId, platform, tokenValue, enabled, revokedAt));
    }

    private DeviceToken build(
            UUID userId,
            String deviceId,
            DevicePlatform platform,
            String tokenValue,
            boolean enabled,
            Instant revokedAt) {
        DeviceToken token = new DeviceToken();
        token.setUserId(userId);
        token.setDeviceId(deviceId);
        token.setPlatform(platform);
        token.setProvider(PushProvider.FCM);
        token.setToken(tokenValue);
        token.setEnabled(enabled);
        token.setRevokedAt(revokedAt);
        token.setLastSeenAt(Instant.now());
        return token;
    }
}
