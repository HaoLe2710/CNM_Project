package fit.iuh.cnm_project_be.auth.dto.response;

import fit.iuh.cnm_project_be.auth.enums.Role;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class LoginResponse {
    String token;
    String userName;
    UUID userId;
    LocalDateTime expiresIn;
    List<Role> roles;
}
