package fit.iuh.cnm_project_be.social.dto.request;

import fit.iuh.cnm_project_be.social.enums.PostVisibilityMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreatePostRequest {

    private String imageUrl;

    private List<CreatePostMediaItemRequest> mediaItems;

    private String caption;

    @NotNull(message = "Visibility mode is required")
    private PostVisibilityMode visibilityMode;

    private List<UUID> allowedViewerIds;

    private List<UUID> taggedFriendIds;
}
