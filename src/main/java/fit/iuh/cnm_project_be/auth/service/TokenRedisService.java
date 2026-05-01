package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
     * Value: {userId}:{platform}
     */
    public void saveRefreshToken(UUID userId, String platform, String refreshToken) {
        String key = "auth:refresh:token:" + refreshToken;
        String value = userId.toString() + ":" + platform;

        redisTemplate.opsForValue().set(key, value, REFRESH_TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        log.info("[RefreshTokenRedis] - Refresh token saved for userId: {}, platform: {}", userId, platform);
    }

    /**
     * Xác thực refresh token từ Redis
     * Trả về tuple [userId, platform] nếu hợp lệ
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

        String[] parts = value.split(":");
        if (parts.length != 2) {
            log.error("[RefreshTokenRedis] - Invalid token format in Redis: {}", key);
            throw new UnauthorizedException("Invalid token data");
        }

        UUID userId = UUID.fromString(parts[0]);
        String platform = parts[1];

        log.debug("[RefreshTokenRedis] - Refresh token validated successfully for userId: {}", userId);
        return new RedisRefreshTokenData(userId, platform);
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

    /**
     * DTO để trả về dữ liệu từ Redis
     */
    public static class RedisRefreshTokenData {
        public final UUID userId;
        public final String platform;

        public RedisRefreshTokenData(UUID userId, String platform) {
            this.userId = userId;
            this.platform = platform;
        }
    }
}
