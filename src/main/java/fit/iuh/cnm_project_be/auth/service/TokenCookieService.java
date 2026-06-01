package fit.iuh.cnm_project_be.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;

/**
 * Service quản lý các tác vụ liên quan đến HTTP Cookies
 * - Set token vào cookie
 * - Xóa cookie
 * - Trích xuất token từ cookie
 */
@Service
@Slf4j
public class TokenCookieService {
    @Value("${app.auth.cookie.secure:${APP_AUTH_COOKIE_SECURE:false}}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie.same-site:${APP_AUTH_COOKIE_SAME_SITE:Strict}}")
    private String cookieSameSite;

    /**
     * Set token vào HTTP Cookie với httpOnly + sameSite protection
     */
    public void setTokenToCookie(
            HttpServletResponse httpServletResponse,
            String cookieName,
            String token,
            Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(resolveSecureFlag())
                .sameSite(resolveSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.debug("[TokenCookie] - Cookie '{}' set with maxAge: {}", cookieName, maxAge);
    }

    /**
     * Xóa tất cả auth cookies từ response
     */
    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie atCookie = ResponseCookie.from("accessToken", "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(resolveSecureFlag())
                .sameSite(resolveSameSite())
                .build();

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(resolveSecureFlag())
                .sameSite(resolveSameSite())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, atCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, rtCookie.toString());
        log.debug("[TokenCookie] - All auth cookies cleared");
    }

    /**
     * Lấy giá trị của cookie theo tên
     */
    public String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Lấy access token từ cookie
     */
    public String getAccessTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, "accessToken");
    }

    /**
     * Lấy refresh token từ cookie
     */
    public String getRefreshTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, "refreshToken");
    }

    private boolean resolveSecureFlag() {
        return cookieSecure || "None".equalsIgnoreCase(resolveSameSite());
    }

    private String resolveSameSite() {
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            return "Strict";
        }

        String normalized = cookieSameSite.trim();
        if ("None".equalsIgnoreCase(normalized)) {
            return "None";
        }
        if ("Lax".equalsIgnoreCase(normalized)) {
            return "Lax";
        }
        if ("Strict".equalsIgnoreCase(normalized)) {
            return "Strict";
        }

        log.warn("[TokenCookie] - Invalid SameSite value '{}', fallback to Strict", cookieSameSite);
        return "Strict";
    }
}
