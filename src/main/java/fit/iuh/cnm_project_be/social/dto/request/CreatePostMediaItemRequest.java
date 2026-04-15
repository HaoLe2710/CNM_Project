package fit.iuh.cnm_project_be.social.dto.request;

import fit.iuh.cnm_project_be.social.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePostMediaItemRequest {

    @NotBlank(message = "Media URL is required")
    private String mediaUrl;

    @NotNull(message = "Media type is required")
    private MediaType mediaType;
}
