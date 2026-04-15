package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.social.enums.PostInteractionScope;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PostInteractionResponse {

    private PostInteractionScope scope;
    private long totalLikeCount;
    private long totalCommentCount;
    private long visibleLikeCount;
    private long visibleCommentCount;
    private List<PostLikeResponse> likes;
    private List<PostCommentResponse> comments;
}
