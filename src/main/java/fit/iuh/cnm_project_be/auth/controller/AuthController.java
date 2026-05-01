
package fit.iuh.cnm_project_be.auth.controller;

import fit.iuh.cnm_project_be.auth.dto.request.ConfirmPasswordChangeRequest;
import fit.iuh.cnm_project_be.auth.dto.request.CheckEmailRequest;
import fit.iuh.cnm_project_be.auth.dto.request.DeviceLoginApprovalRequest;
import fit.iuh.cnm_project_be.auth.dto.request.DeviceLoginRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ForgotPasswordResetRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ForgotPasswordSendOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ForgotPasswordVerifyOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.LoginRequest;
import fit.iuh.cnm_project_be.auth.dto.request.RegisterRequest;
import fit.iuh.cnm_project_be.auth.dto.request.SendOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.VerifyCurrentPasswordRequest;
import fit.iuh.cnm_project_be.auth.dto.request.VerifyOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.LogoutDeviceRequest;
import fit.iuh.cnm_project_be.auth.dto.response.ForgotPasswordVerifyOtpResponse;
import fit.iuh.cnm_project_be.auth.dto.response.CheckEmailResponse;
import fit.iuh.cnm_project_be.auth.dto.response.DeviceLoginQrResponse;
import fit.iuh.cnm_project_be.auth.dto.response.DeviceLoginStatusResponse;
import fit.iuh.cnm_project_be.auth.dto.response.LoginResponse;
import fit.iuh.cnm_project_be.auth.dto.response.RefreshTokenResponse;
import fit.iuh.cnm_project_be.auth.dto.response.VerifyPasswordResponse;
import fit.iuh.cnm_project_be.auth.dto.response.UserDeviceResponseDto;
import fit.iuh.cnm_project_be.auth.service.AuthService;
import fit.iuh.cnm_project_be.auth.service.PasswordService;
import fit.iuh.cnm_project_be.auth.service.OtpService;
import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {
    AuthService authService;
    OtpService otpService;
    PasswordService passwordService;


    @PostMapping("/device-login-request")
    public ApiResponse<DeviceLoginQrResponse> createDeviceLoginRequest(
            @RequestBody @Valid DeviceLoginRequest request
    ) {
        return ApiResponse.ok(
                authService.createDeviceLoginRequest(request),
                UUID.randomUUID().toString()
        );
    }

    @GetMapping("/device-login-status/{approvalId}")
    public ApiResponse<DeviceLoginStatusResponse> getDeviceLoginStatus(
            @PathVariable String approvalId,
            HttpServletResponse response
    ) {
        return ApiResponse.ok(
                authService.getDeviceLoginStatus(approvalId, response),
                UUID.randomUUID().toString()
        );
    }

    @PostMapping("/device-login-approval")
    public ApiResponse<DeviceLoginStatusResponse> approveDeviceLogin(
            @RequestBody @Valid DeviceLoginApprovalRequest request
    ) {
        UUID userId = getCurrentUserId();
        return ApiResponse.ok(
                authService.approveDeviceLogin(userId, request),
                UUID.randomUUID().toString()
        );
    }
    @PostMapping("/register")
    public ApiResponse<?> register(
            @RequestBody @Valid RegisterRequest registerRequest) {

        String requestId = UUID.randomUUID().toString();
        return ApiResponse.ok(authService.register(registerRequest), requestId);
    }

    @PostMapping("/check-email")
    public ApiResponse<CheckEmailResponse> checkEmail(@RequestBody @Valid CheckEmailRequest request) {
        return ApiResponse.ok(authService.checkEmail(request.getEmail()), UUID.randomUUID().toString());
    }

    @GetMapping("/check-existence")
    public ApiResponse<Boolean> checkExistence(@RequestParam String identifier) {
        // Hệ thống tự check xem identifier này là email hay phone đã tồn tại chưa
        boolean isExisted = authService.checkExisted(identifier);

        return ApiResponse.ok(isExisted, UUID.randomUUID().toString());
    }

    // 1. Endpoint gửi OTP đăng ký
    @PostMapping("/send-register-otp")
    public ApiResponse<String> sendRegisterOtp(@RequestBody @Valid SendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (authService.checkExisted(email)) {
            log.warn("[OTP] - Registration blocked: Email {} already exists in system", email);
            throw new BusinessException("This email is already linked to an account. Please login.");
        }

        // 2. Nếu chưa tồn tại, tiến hành gửi OTP
        otpService.sendRegisterOtp(email);

        return ApiResponse.ok("OTP has been sent to your email", UUID.randomUUID().toString());
    }

    // 2. Endpoint xác thực OTP
    @PostMapping("/verify-otp")
    public ApiResponse<String> verifyOtp(@RequestBody @Valid VerifyOtpRequest verifyOtpRequest) {
        // Gọi hàm mới để lấy Token
        String registerToken = otpService.verifyOtpAndGenerateToken(
                verifyOtpRequest.getEmail(),
                verifyOtpRequest.getOtpCode(),
                verifyOtpRequest.getType()
        );

        return ApiResponse.ok(registerToken, UUID.randomUUID().toString());
    }


    @PostMapping("/login")
    public ApiResponse<?> login(
            @RequestBody @Valid LoginRequest loginRequest,
            HttpServletResponse httpServletResponse
    ) {
        LoginResponse response = authService.login(loginRequest, httpServletResponse);

        String requestId = UUID.randomUUID().toString();

        return ApiResponse.ok(response, requestId);
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.ok("Logout successfully", UUID.randomUUID().toString());
    }

    @PostMapping("/refresh-token")
    public ApiResponse<RefreshTokenResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        RefreshTokenResponse result = authService.refreshToken(request, response);
        return ApiResponse.ok(result, UUID.randomUUID().toString());
    }

    /**
     * Endpoint: Xác thực mật khẩu hiện tại và nhận changePasswordToken
     * POST /api/v1/auth/verify-current-password
     * Requires: JWT Token (Authenticated)
     */
    @PostMapping("/verify-current-password")
    public ApiResponse<VerifyPasswordResponse> verifyCurrentPassword(
            @RequestBody @Valid VerifyCurrentPasswordRequest request
    ) {
        // Lấy userId từ JWT token
        UUID userId = getCurrentUserId();

        log.info("[ChangePassword] - Verifying current password for userId: {}", userId);
        
        String changePasswordToken = passwordService.verifyCurrentPassword(
                userId,
                request.getCurrentPassword()
        );

        VerifyPasswordResponse response = VerifyPasswordResponse.builder()
                .changePasswordToken(changePasswordToken)
                .message("Current password verified. You can now change your password.")
                .expiresIn(600L) // 10 minutes in seconds
                .build();

        String requestId = UUID.randomUUID().toString();
        return ApiResponse.ok(response, requestId);
    }

    /**
     * Endpoint: Đổi mật khẩu sử dụng changePasswordToken
     * POST /api/v1/auth/change-password
     * Requires: JWT Token (Authenticated)
     */
    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(
            @RequestBody @Valid ConfirmPasswordChangeRequest request
    ) {
        // Lấy userId từ JWT token
        UUID userId = getCurrentUserId();

        log.info("[ChangePassword] - Attempting to change password for userId: {}", userId);

        passwordService.confirmPasswordChange(userId, request);

        String requestId = UUID.randomUUID().toString();
        return ApiResponse.ok("Password has been changed successfully", requestId);
    }

    /**
     * Endpoint: Gửi OTP để xác thực email/phone khi quên mật khẩu
     * POST /api/v1/auth/forgot-password/send-otp
     * Public endpoint (không cần authentication)
     */
    @PostMapping("/forgot-password/send-otp")
    public ApiResponse<String> sendForgotPasswordOtp(
            @RequestBody @Valid ForgotPasswordSendOtpRequest request
    ) {
        String identifier = request.getIdentifier();

        log.info("[ForgotPassword] - Send OTP request for: {}", identifier.replaceAll("(?<=.{2}).", "*"));

        passwordService.sendForgotPasswordOtp(identifier);

        return ApiResponse.ok("OTP has been sent to your email or phone", UUID.randomUUID().toString());
    }

    /**
     * Endpoint: Xác thực OTP và nhận resetToken
     * POST /api/v1/auth/forgot-password/verify-otp
     * Public endpoint (không cần authentication)
     *
     * Response: resetToken (sử dụng trong bước reset password)
     */
    @PostMapping("/forgot-password/verify-otp")
    public ApiResponse<ForgotPasswordVerifyOtpResponse> verifyForgotPasswordOtp(
            @RequestBody @Valid ForgotPasswordVerifyOtpRequest request
    ) {
        String identifier = request.getIdentifier();
        String otp = request.getOtp();

        log.info("[ForgotPassword] - Verify OTP request for: {}", identifier.replaceAll("(?<=.{2}).", "*"));

        ForgotPasswordVerifyOtpResponse response = passwordService.verifyForgotPasswordOtp(identifier, otp);

        return ApiResponse.ok(response, UUID.randomUUID().toString());
    }

    /**
     * Endpoint: Reset mật khẩu sử dụng resetToken
     * POST /api/v1/auth/forgot-password/reset
     * Public endpoint (không cần authentication)
     */
    @PostMapping("/forgot-password/reset")
    public ApiResponse<String> resetForgotPassword(
            @RequestBody @Valid ForgotPasswordResetRequest request
    ) {
        String identifier = request.getIdentifier();

        log.info("[ForgotPassword] - Reset password request for: {}", identifier.replaceAll("(?<=.{2}).", "*"));

        passwordService.resetPassword(request);

        String requestId = UUID.randomUUID().toString();
        return ApiResponse.ok("Password has been reset successfully. Please login with your new password.", requestId);
    }

    /**
     * Endpoint: Lấy danh sách tất cả devices của user hiện tại
     * GET /api/v1/auth/devices
     * Requires: JWT Token (Authenticated)
     */
    @GetMapping("/devices")
    public ApiResponse<List<UserDeviceResponseDto>> getDevices() {
        UUID userId = getCurrentUserId();
        log.info("[Device] - Getting devices for userId: {}", userId);
        
        List<UserDeviceResponseDto> devices = authService.getDevices(userId);
        return ApiResponse.ok(devices, UUID.randomUUID().toString());
    }

    /**
     * Endpoint: Đăng xuất khỏi device cụ thể
     * POST /api/v1/auth/logout-device
     * Requires: JWT Token (Authenticated)
     */
    @PostMapping("/logout-device")
    public ApiResponse<String> logoutDevice(@RequestBody @Valid LogoutDeviceRequest request) {
        UUID userId = getCurrentUserId();
        log.info("[Device] - Logout device {} on platform {} for userId: {}", 
                 request.getDeviceId(), request.getPlatform(), userId);
        
        authService.logoutDevice(userId, request.getDeviceId(), request.getPlatform());
        return ApiResponse.ok("Device logged out successfully", UUID.randomUUID().toString());
    }

    /**
     * Utility method: Lấy userId từ JWT token trong SecurityContext
     */
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String userIdStr = jwt.getClaim("userId");

            if (userIdStr != null) {
                try {
                    return UUID.fromString(userIdStr);
                } catch (IllegalArgumentException e) {
                    log.error("[Auth] - Invalid userId format in token: {}", userIdStr);
                    throw new fit.iuh.cnm_project_be.common.exception.UnauthorizedException("Invalid user ID in token");
                }
            }
        }

        log.warn("[Auth] - Unable to extract userId from token");
        throw new fit.iuh.cnm_project_be.common.exception.UnauthorizedException("Unauthorized: User ID not found in token");
    }
}


