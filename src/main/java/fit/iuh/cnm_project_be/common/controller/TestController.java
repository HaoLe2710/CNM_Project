package fit.iuh.cnm_project_be.common.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.auth.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test")
@Validated
@RequiredArgsConstructor
public class TestController {
    private final JwtUtils jwtUtils;

    /**
     * 1️⃣ Test success response
     */
    @GetMapping("/success")
    public ApiResponse<String> success(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        return ApiResponse.ok("Everything works", requestId);
    }

    /**
     * 2️⃣ Test NotFoundException
     */
    @GetMapping("/not-found")
    public void notFound() {
        throw new NotFoundException("Resource not found (test)");
    }

    /**
     * 3️⃣ Test BusinessException
     */
    @GetMapping("/business-error")
    public void businessError() {
        throw new BusinessException("Business rule violated (test)");
    }

    /**
     * 4️⃣ Test validation error (@Valid body)
     */
    @PostMapping("/validate")
    public ApiResponse<String> validate(
            @RequestBody @Validated TestRequest request,
            HttpServletRequest http) {

        String requestId = resolveRequestId(http);
        return ApiResponse.ok("Valid input: " + request.getName(), requestId);
    }

    /**
     * 5️⃣ Test constraint violation (query param)
     */
    @GetMapping("/param")
    public String param(@RequestParam @NotBlank String name) {
        return "Hello " + name;
    }

    /**
     * 6️⃣ Verify JWT with POST body: {"TOKEN": "..."}
     */
    @PostMapping("/jwt/verify")
    public ResponseEntity<ApiResponse<?>> verifyJwt(
            @RequestBody @Valid JwtVerifyRequest request,
            HttpServletRequest http) {

        String requestId = resolveRequestId(http);

        try {
            Jwt jwt = jwtUtils.decodeToken(request.getToken());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("valid", true);
            data.put("subject", jwt.getSubject());
            data.put("issuer", jwt.getIssuer() != null ? jwt.getIssuer().toString() : null);
            data.put("issuedAt", jwt.getIssuedAt());
            data.put("expiresAt", jwt.getExpiresAt());
            data.put("claims", jwt.getClaims());

            return ResponseEntity.ok(ApiResponse.ok(data, requestId));
        } catch (JwtException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail(
                            "JWT_INVALID",
                            "Token is invalid or expired",
                            ex.getMessage(),
                            requestId
                    ));
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return (requestId != null && !requestId.isBlank())
                ? requestId
                : UUID.randomUUID().toString();
    }

    @Data
    static class TestRequest {
        @NotBlank(message = "Name must not be blank")
        private String name;
    }

    @Data
    static class JwtVerifyRequest {
        @NotBlank(message = "TOKEN must not be blank")
        @JsonAlias({"TOKEN", "token"})
        private String token;
    }
}