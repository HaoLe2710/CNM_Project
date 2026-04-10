package fit.iuh.cnm_project_be.auth.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class TokenCookieService {

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
                .secure(false) // Set to true when using HTTPS
                .sameSite("Strict")
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
                .build();

        ResponseCookie rtCookie = ResponseCookie.from("refreshToken", "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
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
}
