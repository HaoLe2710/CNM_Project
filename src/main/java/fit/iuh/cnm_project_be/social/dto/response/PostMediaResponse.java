package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.social.enums.MediaType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PostMediaResponse {

    private UUID id;
    private String mediaUrl;
    private MediaType mediaType;
    private Integer sortOrder;
}
