package fit.iuh.cnm_project_be.admin.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AdminUserItemResponse {
    private UUID userId;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Instant createdAt;
    private Instant bannedUntil;
    private boolean bannedNow;
}

