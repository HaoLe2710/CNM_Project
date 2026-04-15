package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.social.enums.PostVisibilityMode;
import fit.iuh.cnm_project_be.user.dto.response.UserSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PostAudienceResponse {

    private PostVisibilityMode visibilityMode;
    private List<UserSummaryResponse> taggedFriends;
    private List<UserSummaryResponse> allowedViewers;
}
