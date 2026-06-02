package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.response.RefreshTokenResponse;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.auth.utils.JwtUtils;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Service xử lý Refresh Token logic
 * - Extract token từ cookie
 * - Validate refresh token từ Redis
 * - Generate access token mới
 * - Set cookie mới
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class AuthRefreshService {
    static long ACCESS_TOKEN_EXPIRE_MINUTES = 15;

    AccountRepository accountRepository;
    JwtUtils jwtUtils;
    TokenCookieService tokenCookieService;
    TokenRedisService tokenRedisService;

    @Transactional
    public RefreshTokenResponse refreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 1. Extract refresh token từ cookie
            String refreshToken = tokenCookieService.getRefreshTokenFromCookie(request);

            // 2. Validate refresh token từ Redis
            // Nếu RT hết hạn/invalid sẽ throw UnauthorizedException
            TokenRedisService.RedisRefreshTokenData tokenData = tokenRedisService.validateRefreshToken(refreshToken);

            // 3. Lấy account info
            Account account = accountRepository.findByUserIdAndDeletedAtIsNull(tokenData.userId)
                    .orElseThrow(() -> new NotFoundException("Account not found"));

            // 4. Generate access token mới
            String newAccessToken = jwtUtils.generateToken(account, tokenData.deviceId, tokenData.platform);

            // 5. Set cookie mới (httpOnly)
            tokenCookieService.setTokenToCookie(response, "accessToken", newAccessToken, Duration.ofMinutes(ACCESS_TOKEN_EXPIRE_MINUTES));

            log.info("[AuthRefresh] - Access token refreshed successfully for userId: {}", tokenData.userId);

            return RefreshTokenResponse.builder()
                    .message("Access token refreshed successfully")
                    .build();

        } catch (UnauthorizedException ex) {
            // Do not clear cookies here: a stale refresh request can race with a
            // successful login response and wipe the newly issued auth cookies.
            log.warn("[AuthRefresh] - Refresh token invalid/expired: {}", ex.getMessage());
            throw new UnauthorizedException("Refresh token expired. Please login again.");
        }
    }
}
