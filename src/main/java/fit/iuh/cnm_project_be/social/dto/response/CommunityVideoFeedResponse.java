package fit.iuh.cnm_project_be.social.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CommunityVideoFeedResponse {
    private List<MomentResponse> items;
    private String nextCursor;
    private boolean hasMore;
}
