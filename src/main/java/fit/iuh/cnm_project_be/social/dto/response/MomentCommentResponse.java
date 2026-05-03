package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.user.dto.response.UserSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class MomentCommentResponse {
    private UUID id;
    private UserSummaryResponse user;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
}
