package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.request.LoginRequest;
import fit.iuh.cnm_project_be.auth.dto.response.LoginResponse;
import fit.iuh.cnm_project_be.auth.entity.Account;
import fit.iuh.cnm_project_be.auth.repository.AccountRepository;
import fit.iuh.cnm_project_be.auth.utils.JwtUtils;
import fit.iuh.cnm_project_be.common.exception.UnauthorizedException;
import fit.iuh.cnm_project_be.user.service.UserDeviceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class LoginService {
        static long ACCESS_TOKEN_EXPIRE_MINUTES = 15;

        AccountRepository accountRepository;
        PasswordEncoder passwordEncoder;
        JwtUtils jwtUtils;
        UserDeviceService userDeviceService;
        TokenCookieService tokenCookieService;
        TokenRedisService tokenRedisService;
        DeviceLoginService deviceLoginService;

        @Transactional
        public LoginResponse login(LoginRequest request, HttpServletResponse response) {
                String username = normalizeIdentifier(request.getUsername());

                Account account = accountRepository.findByEmailOrPhoneAndDeletedAtIsNull(username, username)
                                .orElseThrow(() -> new UnauthorizedException("Account does not exist"));

                if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
                        throw new UnauthorizedException("Incorrect password");
                }

                String platform = request.getPlatform().toString().toLowerCase();

                if (deviceLoginService.shouldRequireApproval(
                                account.getUserId(),
                                request.getDeviceId(),
                                request.getPlatform())) {
                        log.info("[Login] - Approval required for user {} on new device {} ({})",
                                        account.getUserId(), request.getDeviceId(), request.getPlatform());
                        return deviceLoginService.createCredentialApprovalRequest(account, request);
                }

                userDeviceService.saveOrUpdateDevice(
                                account.getUserId(),
                                request.getDeviceId(),
                                request.getPlatform(),
                                request.getDeviceName());

                String accessToken = jwtUtils.generateToken(account, request.getDeviceId(), platform);
                String refreshToken = jwtUtils.generateRefreshToken();

                // Keep exactly one active session token per platform slot.
                tokenRedisService.deleteRefreshTokensByScope(account.getUserId(), platform);
                tokenRedisService.saveRefreshToken(account.getUserId(), platform, request.getDeviceId(), refreshToken);

                tokenCookieService.setTokenToCookie(
                                response,
                                "accessToken",
                                accessToken,
                                Duration.ofMinutes(ACCESS_TOKEN_EXPIRE_MINUTES));
                tokenCookieService.setTokenToCookie(response, "refreshToken", refreshToken, Duration.ofDays(30));

                log.info("[Login] - User {} logged in successfully on platform {}", account.getUserId(), platform);

                return LoginResponse.builder()
                                .email(account.getEmail())
                                .phone(account.getPhone())
                                .userId(account.getUserId())
                                .roles(account.getRoles())
                                .status("SUCCESS")
                                .deviceName(request.getDeviceName())
                                .platform(request.getPlatform().name())
                                .build();
        }

        private String normalizeIdentifier(String identifier) {
                String trimmedValue = identifier == null ? "" : identifier.trim();
                if (trimmedValue.contains("@")) {
                        return trimmedValue.toLowerCase(Locale.ROOT);
                }
                return trimmedValue;
        }
}
