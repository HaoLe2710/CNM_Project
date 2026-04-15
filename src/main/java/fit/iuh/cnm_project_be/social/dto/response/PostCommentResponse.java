package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.user.dto.response.UserSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PostCommentResponse {

    private UUID id;
    private UUID parentCommentId;
    private UserSummaryResponse user;
    private String content;
    private long likeCount;
    private boolean likedByCurrentUser;
    private Instant createdAt;
    private Instant updatedAt;
    private List<PostCommentResponse> replies;
}
