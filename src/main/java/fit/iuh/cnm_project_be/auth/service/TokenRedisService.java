package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service quản lý Token trong Redis
 * - Lưu refresh token
 * - Xác thực refresh token
 * - Xóa refresh token
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class TokenRedisService {
    static long REFRESH_TOKEN_EXPIRE_DAYS = 30;

    StringRedisTemplate redisTemplate;

    /**
     * Lưu refresh token vào Redis với format key: auth:refresh:token:{token}
     * Value: {userId}:{platform}:{deviceId}
     */
    public void saveRefreshToken(UUID userId, String platform, String deviceId, String refreshToken) {
        String key = "auth:refresh:token:" + refreshToken;
        String value = buildTokenValue(userId, platform, deviceId);

        redisTemplate.opsForValue().set(key, value, REFRESH_TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        log.info("[RefreshTokenRedis] - Refresh token saved for userId: {}, platform: {}, deviceId: {}",
                userId, platform, deviceId);
    }

    /**
     * Backward compatible overload for existing callers.
     */
    public void saveRefreshToken(UUID userId, String platform, String refreshToken) {
        saveRefreshToken(userId, platform, null, refreshToken);
    }

    /**
     * Xác thực refresh token từ Redis
     * Trả về tuple [userId, platform, deviceId] nếu hợp lệ
     * Ném exception nếu không hợp lệ
     */
    public RedisRefreshTokenData validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is missing");
        }

        String key = "auth:refresh:token:" + refreshToken;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            log.warn("[RefreshTokenRedis] - Refresh token not found or expired: {}", key);
            throw new UnauthorizedException("Refresh token is invalid or has expired");
        }

        RedisRefreshTokenData tokenData;
        try {
            tokenData = parseTokenValue(value);
        } catch (Exception ex) {
            log.error("[RefreshTokenRedis] - Invalid token format in Redis: {}", key);
            throw new UnauthorizedException("Invalid token data");
        }

        log.debug("[RefreshTokenRedis] - Refresh token validated successfully for userId: {}", tokenData.userId);
        return tokenData;
    }

    /**
     * Xóa refresh token khỏi Redis (logout hoặc invalid token)
     */
    public void deleteRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String key = "auth:refresh:token:" + refreshToken;
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("[RefreshTokenRedis] - Refresh token deleted: {}", key);
        } else {
            log.warn("[RefreshTokenRedis] - Refresh token not found, already deleted: {}", key);
        }
    }

    public Optional<RedisRefreshTokenData> findRefreshTokenData(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }

        String key = "auth:refresh:token:" + refreshToken;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(parseTokenValue(value));
        } catch (Exception ex) {
            log.warn("[RefreshTokenRedis] - Invalid refresh token data for key {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    public void deleteRefreshTokensByScope(UUID userId, String platform, String deviceId) {
        if (userId == null || platform == null || platform.isBlank()) {
            return;
        }

        Set<String> keys = redisTemplate.keys("auth:refresh:token:*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        String normalizedPlatform = platform.toLowerCase();

        keys.forEach(key -> {
            String value = redisTemplate.opsForValue().get(key);
            if (!matchesScope(value, userId, normalizedPlatform, deviceId)) {
                return;
            }

            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("[RefreshTokenRedis] - Scoped refresh token deleted: key={}, userId={}, platform={}, deviceId={}",
                        key, userId, normalizedPlatform, deviceId);
            }
        });
    }

    public void deleteRefreshTokensByScope(UUID userId, String platform) {
        deleteRefreshTokensByScope(userId, platform, null);
    }

    private String buildTokenValue(UUID userId, String platform, String deviceId) {
        String normalizedPlatform = platform == null ? "" : platform.toLowerCase();
        String normalizedDeviceId = deviceId == null ? "" : deviceId.trim();
        return userId + ":" + normalizedPlatform + ":" + normalizedDeviceId;
    }

    private boolean matchesScope(String value, UUID userId, String platform, String deviceId) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String[] parts = value.split(":", 3);
        if (parts.length < 2) {
            return false;
        }

        if (!userId.toString().equals(parts[0])) {
            return false;
        }

        if (!platform.equalsIgnoreCase(parts[1])) {
            return false;
        }

        if (deviceId == null || deviceId.isBlank()) {
            return true;
        }

        if (parts.length < 3) {
            return false;
        }

        return Objects.equals(parts[2], deviceId);
    }

    private RedisRefreshTokenData parseTokenValue(String value) {
        String[] parts = value.split(":", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid token value format");
        }

        UUID userId = UUID.fromString(parts[0]);
        String platform = parts[1];
        String deviceId = parts.length >= 3 ? parts[2] : null;
        if (deviceId != null && deviceId.isBlank()) {
            deviceId = null;
        }

        return new RedisRefreshTokenData(userId, platform, deviceId);
    }

    /**
     * DTO để trả về dữ liệu từ Redis
     */
    public static class RedisRefreshTokenData {
        public final UUID userId;
        public final String platform;
        public final String deviceId;

        public RedisRefreshTokenData(UUID userId, String platform, String deviceId) {
            this.userId = userId;
            this.platform = platform;
            this.deviceId = deviceId;
        }
    }
}
