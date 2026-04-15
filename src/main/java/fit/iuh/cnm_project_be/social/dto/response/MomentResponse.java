package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.social.enums.MediaType;
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
    private MediaType mediaType;
    private String caption;
    private Instant createdAt;
}
