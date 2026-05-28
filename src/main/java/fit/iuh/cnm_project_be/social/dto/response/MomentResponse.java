package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.social.enums.MediaType;
import fit.iuh.cnm_project_be.social.enums.MomentAudioMode;
import fit.iuh.cnm_project_be.social.enums.MomentVisibilityMode;
import fit.iuh.cnm_project_be.user.dto.response.UserSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class MomentResponse {

    private UUID id;
    private UserSummaryResponse author;
    private String mediaUrl;
    private String coverUrl;
    private MediaType mediaType;
    private String caption;
    private Integer durationSeconds;
    private MomentVisibilityMode visibilityMode;
    private MomentAudioMode audioMode;
    private String musicTrackId;
    private String musicTitle;
    private String musicArtist;
    private String musicUrl;
    private Integer musicStartSeconds;
    private long likeCount;
    private long commentCount;
    private long shareCount;
    private long viewCount;
    private boolean likedByCurrentUser;
    private boolean followedByCurrentUser;
    private Instant createdAt;
}
