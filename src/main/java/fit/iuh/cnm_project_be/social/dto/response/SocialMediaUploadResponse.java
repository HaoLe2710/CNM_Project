package fit.iuh.cnm_project_be.social.dto.response;

import fit.iuh.cnm_project_be.social.enums.MediaType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SocialMediaUploadResponse {

    private String url;
    private String storageKey;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private MediaType mediaType;
}
