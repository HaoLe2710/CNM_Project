package fit.iuh.cnm_project_be.user.controller;

import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor

public class UserController {

    @Autowired
    private UserService userService;
    // Trong UserController hoặc AuthController
    @PostMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {
        UUID userId = UUID.fromString(jwt.getSubject()); // subject = userId???Nhớ check lại cấu hình JWT
        userService.updateFcmToken(userId, body.get("token"));
        return ResponseEntity.ok().build();
    }

}
