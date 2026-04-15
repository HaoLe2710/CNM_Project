package fit.iuh.cnm_project_be.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePostCommentRequest {

    @NotBlank(message = "Comment content is required")
    private String content;
}
