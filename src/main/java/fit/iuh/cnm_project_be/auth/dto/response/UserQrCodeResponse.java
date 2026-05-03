package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserQrCodeResponse {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String inviteLink;
    private String qrCodeUrl;
    private String qrContent;
    private Instant expiresAt;
}
