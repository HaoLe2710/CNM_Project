package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.utils.JwtUtils;
import fit.iuh.cnm_project_be.user.enums.Platform;
import fit.iuh.cnm_project_be.user.service.UserDeviceService;
import fit.iuh.cnm_project_be.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service xu ly Logout logic
 * - Xoa refresh token tu Redis
 * - Xoa authentication context
 * - Xoa cookies
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class LogoutService {

    TokenCookieService tokenCookieService;
    TokenRedisService tokenRedisService;
    JwtUtils jwtUtils;
    UserDeviceService userDeviceService;
    UserService userService;

    @Transactional
    public boolean logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = tokenCookieService.getAccessTokenFromCookie(request);
        String refreshToken = tokenCookieService.getRefreshTokenFromCookie(request);
        TokenRedisService.RedisRefreshTokenData refreshScope = tokenRedisService
                .findRefreshTokenData(refreshToken)
                .orElse(null);

        try {
            // 1. Cleanup theo scope thiet bi hien tai.
            cleanupCurrentDeviceScope(accessToken, refreshScope);

            // 2. Xoa refresh token hien tai (neu co)
            if (refreshToken != null && !refreshToken.isBlank()) {
                tokenRedisService.deleteRefreshToken(refreshToken);
            }

            // 3. Xoa authentication context
            SecurityContextHolder.clearContext();
            log.info("[Logout] - Logout successful, authentication cleared");

        } catch (Exception e) {
            log.error("[Logout] - Error during logout: {}", e.getMessage());
            // Van tiep tuc xoa cookies du co loi
        } finally {
            // 4. Luon xoa cookies
            tokenCookieService.clearAuthCookies(response);
        }

        return true;
    }

    private void cleanupCurrentDeviceScope(String accessToken, TokenRedisService.RedisRefreshTokenData refreshScope) {
        try {
            UUID userId = null;
            String platformRaw = null;
            String deviceId = null;

            if (accessToken != null && !accessToken.isBlank()) {
                String userIdRaw = jwtUtils.getUserIdFromToken(accessToken);
                platformRaw = jwtUtils.getPlatformFromToken(accessToken);
                deviceId = jwtUtils.getDeviceIdFromToken(accessToken);

                if (userIdRaw != null && !userIdRaw.isBlank()) {
                    userId = UUID.fromString(userIdRaw);
                }
            } else if (refreshScope != null) {
                userId = refreshScope.userId;
                platformRaw = refreshScope.platform;
                deviceId = refreshScope.deviceId;
            }

            if (userId == null || platformRaw == null || platformRaw.isBlank()) {
                return;
            }

            String normalizedPlatform = platformRaw.toLowerCase();
            tokenRedisService.deleteRefreshTokensByScope(userId, normalizedPlatform, deviceId);

            if (deviceId != null && !deviceId.isBlank()) {
                userDeviceService.deleteDevice(userId, deviceId, platformRaw);
            }

            Platform platform = Platform.valueOf(platformRaw.toUpperCase());
            if (platform == Platform.ANDROID || platform == Platform.IOS) {
                userService.updateFcmToken(userId, null);
            }
        } catch (Exception ex) {
            log.warn("[Logout] - Could not cleanup device scope from access/refresh token: {}", ex.getMessage());
        }
    }
}
