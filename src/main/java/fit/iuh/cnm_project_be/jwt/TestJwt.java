package fit.iuh.cnm_project_be.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
public class TestJwt {
    private final JwtUtils jwtUtils;

    @GetMapping("/token")
    public String genToken() {
        return jwtUtils.generateToken("tai");
    }

    @GetMapping("/verify")
    public Object verifyToken(@RequestParam("token") String token) {
        try {
            // Nếu thành công, trả về các claims (sub, iss, exp...)
            return jwtUtils.decodeToken(token).getClaims();
        } catch (org.springframework.security.oauth2.jwt.JwtValidationException e) {
            // Lỗi phổ biến nhất: Hết hạn (Expired) hoặc sai Issuer
            return Map.of(
                    "status", "invalid",
                    "reason", "Validation failed",
                    "details", e.getErrors().toString()
            );
        } catch (org.springframework.security.oauth2.jwt.BadJwtException e) {
            // Lỗi: Chữ ký không khớp (Signature invalid) hoặc Token rác
            return Map.of(
                    "status", "invalid",
                    "reason", "Bad JWT",
                    "message", e.getMessage()
            );
        } catch (Exception e) {
            // Các lỗi hệ thống khác
            e.printStackTrace(); // In ra Console của IntelliJ để xem dòng lỗi
            return Map.of(
                    "status", "error",
                    "message", e.getMessage()
            );
        }
    }
}
