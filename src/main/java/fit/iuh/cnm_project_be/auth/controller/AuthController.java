package fit.iuh.cnm_project_be.auth.controller;

import fit.iuh.cnm_project_be.auth.dto.request.LoginRequest;
import fit.iuh.cnm_project_be.auth.dto.request.RegisterRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ChangePasswordRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ForgotPasswordResetRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ForgotPasswordSendOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.ForgotPasswordVerifyOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.SendRegisterOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.request.VerifyRegisterOtpRequest;
import fit.iuh.cnm_project_be.auth.dto.response.ForgotPasswordVerifyOtpResponse;
import fit.iuh.cnm_project_be.auth.dto.response.LoginResponse;
import fit.iuh.cnm_project_be.auth.dto.response.TokenResponse;
import fit.iuh.cnm_project_be.auth.dto.response.VerifyRegisterOtpResponse;
import fit.iuh.cnm_project_be.auth.service.AuthService;
import fit.iuh.cnm_project_be.auth.service.RefreshTokenService;
import fit.iuh.cnm_project_be.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    AuthService authService;
    private final RefreshTokenService refreshTokenService;

        @PostMapping("/register/send-otp")
        public ApiResponse<String> sendRegisterOtp(
                        @RequestBody @Valid SendRegisterOtpRequest request
        ) {
                authService.sendRegisterOtp(request.getEmail());
                return ApiResponse.ok("OTP has been sent to your email", UUID.randomUUID().toString());
        }

        @PostMapping("/register/verify-otp")
        public ApiResponse<VerifyRegisterOtpResponse> verifyRegisterOtp(
                        @RequestBody @Valid VerifyRegisterOtpRequest request
        ) {
                VerifyRegisterOtpResponse response = authService.verifyRegisterOtp(request.getEmail(), request.getOtp());
                return ApiResponse.ok(response, UUID.randomUUID().toString());
        }

    @PostMapping("/forgot-password/send-otp")
    public ApiResponse<String> sendForgotPasswordOtp(
            @RequestBody @Valid ForgotPasswordSendOtpRequest request
    ) {
        authService.sendForgotPasswordOtp(request.getIdentifier());
        return ApiResponse.ok("OTP has been sent to linked email", UUID.randomUUID().toString());
    }

    @PostMapping("/forgot-password/verify-otp")
    public ApiResponse<ForgotPasswordVerifyOtpResponse> verifyForgotPasswordOtp(
            @RequestBody @Valid ForgotPasswordVerifyOtpRequest request
    ) {
        ForgotPasswordVerifyOtpResponse response = authService.verifyForgotPasswordOtp(
                request.getIdentifier(),
                request.getOtp()
        );
        return ApiResponse.ok(response, UUID.randomUUID().toString());
    }

    @PostMapping("/forgot-password/reset")
    public ApiResponse<String> resetForgottenPassword(
            @RequestBody @Valid ForgotPasswordResetRequest request
    ) {
        authService.resetForgottenPassword(request);
        return ApiResponse.ok("Reset password successfully", UUID.randomUUID().toString());
    }

    @PostMapping("/register")
    public ApiResponse<?> register(
            @RequestBody @Valid RegisterRequest registerRequest) {

        String requestId = UUID.randomUUID().toString();
        return ApiResponse.ok(authService.register(registerRequest), requestId);
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
        authService.logout(request);

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

        String id =  UUID.randomUUID().toString();

        return ApiResponse.ok("Logout successfully", id);
    }

    @PostMapping("/refresh-token")
    public ApiResponse<LoginResponse> handleRefreshToken(
            @CookieValue(name = "refreshToken") String oldRefreshToken,
            HttpServletResponse response
    ) {
        TokenResponse tokenResponse = refreshTokenService.rotateRefreshToken(oldRefreshToken);

        ResponseCookie newCookie = ResponseCookie.from("refreshToken", tokenResponse.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());

        LoginResponse loginResponse = LoginResponse.builder()
                .token(tokenResponse.getAccessToken())
                .build();

        String requestId = UUID.randomUUID().toString();

        return ApiResponse.ok(loginResponse, requestId);
    }

        @PutMapping("/change-password")
        public ApiResponse<String> changePassword(
                        @RequestBody @Valid ChangePasswordRequest request
        ) {
                authService.changePassword(request);
                return ApiResponse.ok("Change password successfully", UUID.randomUUID().toString());
        }
}
