package fit.iuh.cnm_project_be.auth.service;

import fit.iuh.cnm_project_be.auth.dto.request.LoginRequest;
import fit.iuh.cnm_project_be.auth.dto.request.RegisterRequest;
import fit.iuh.cnm_project_be.auth.dto.response.LoginResponse;
import fit.iuh.cnm_project_be.auth.dto.response.RegisterResponse;
import fit.iuh.cnm_project_be.auth.dto.response.RefreshTokenResponse;
import fit.iuh.cnm_project_be.auth.dto.response.UserDeviceResponseDto;
import fit.iuh.cnm_project_be.user.service.UserDeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Facade Service - Điều phối các service con
 * Đây là entry point chính cho tất cả auth operations
 * 
 * Services:
 * - LoginService: Xử lý login logic
 * - AuthRefreshService: Xử lý refresh token logic
 * - LogoutService: Xử lý logout logic
 * - RegistrationService: Xử lý register logic
 * - AccountService: Quản lý account operations
 * - TokenCookieService: Quản lý HTTP cookies
 * - TokenRedisService: Quản lý Redis refresh tokens
 * - UserDeviceService: Quản lý thiết bị của user
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Slf4j
public class AuthService {

    LoginService loginService;
    AuthRefreshService authRefreshService;
    LogoutService logoutService;
    RegistrationService registrationService;
    AccountService accountService;
    UserDeviceService userDeviceService;

    /**
     * Đăng nhập và tạo tokens (access + refresh)
     */
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        return loginService.login(request, response);
    }

    /**
     * Làm mới access token bằng refresh token
     * Nếu RT hết hạn thì đăng xuất ngay lập tức
     */
    public RefreshTokenResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        return authRefreshService.refreshAccessToken(request, response);
    }

    /**
     * Đăng ký tài khoản mới
     */
    public RegisterResponse register(RegisterRequest request) {
        return registrationService.register(request);
    }

    /**
     * Kiểm tra email/phone có tồn tại chưa
     */
    public boolean checkExisted(String identifier) {
        return accountService.checkExisted(identifier);
    }

    /**
     * Đăng xuất
     */
    public boolean logout(HttpServletRequest request, HttpServletResponse httpServletResponse) {
        return logoutService.logout(request, httpServletResponse);
    }

    /**
     * Soft delete account theo userId
     */
    public void softDeleteAccountByUserId(java.util.UUID userId) {
        accountService.softDeleteAccountByUserId(userId);
    }

    /**
     * Soft delete cả user và account
     */
    public void softDeleteUserAndAccount(java.util.UUID userId) {
        accountService.softDeleteUserAndAccount(userId);
    }

    /**
     * Lấy danh sách tất cả devices của user
     */
    public List<UserDeviceResponseDto> getDevices(UUID userId) {
        return userDeviceService.getUserDeviceByUserId(userId)
                .stream()
                .map(device -> UserDeviceResponseDto.builder()
                        .id(device.getId())
                        .deviceId(device.getDeviceId())
                        .platform(device.getPlatform())
                        .deviceName(device.getDeviceName())
                        .lastSeenAt(device.getLastSeenAt())
                        .createdAt(device.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Đăng xuất device cụ thể
     */
    public void logoutDevice(UUID userId, String deviceId, String platform) {
        userDeviceService.deleteDevice(userId, deviceId, platform);
        log.info("[Device Logout] - User {} logged out from device {} on platform {}", userId, deviceId, platform);
    }
}

