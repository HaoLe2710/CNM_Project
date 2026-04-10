package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý Logout logic
 * - Xóa refresh token từ Redis
 * - Clear authentication context
 * - Xóa cookies
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class LogoutService {

    TokenCookieService tokenCookieService;
    TokenRedisService tokenRedisService;

    @Transactional
    public boolean logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 1. Lấy Refresh Token từ Cookie
            String refreshToken = tokenCookieService.getRefreshTokenFromCookie(request);

            // 2. Xóa Refresh Token từ Redis (nếu có)
            if (refreshToken != null && !refreshToken.isBlank()) {
                tokenRedisService.deleteRefreshToken(refreshToken);
            }

            // 3. Xóa authentication context
            SecurityContextHolder.clearContext();
            log.info("[Logout] - Logout successful, authentication cleared");

        } catch (Exception e) {
            log.error("[Logout] - Error during logout: {}", e.getMessage());
            // Vẫn tiếp tục xóa cookies dù có lỗi
        } finally {
            // 4. Luôn xóa cookies
            tokenCookieService.clearAuthCookies(response);
        }

        return true;
    }
}
