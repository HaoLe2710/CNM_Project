package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.social.enums.PostVisibilityMode;
import fit.iuh.cnm_project_be.social.enums.PostInteractionScope;
import fit.iuh.cnm_project_be.user.dto.response.UserSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PostResponse {

    private UUID id;
    private UserSummaryResponse author;
    private String imageUrl;
    private List<PostMediaResponse> mediaItems;
    private String caption;
    private boolean archived;
    private Instant archivedAt;
    private PostVisibilityMode visibilityMode;
    private List<UserSummaryResponse> taggedFriends;
    private long likeCount;
    private long commentCount;
    private boolean likedByCurrentUser;
    private PostInteractionScope interactionScope;
    private Instant createdAt;
    private Instant updatedAt;
}
