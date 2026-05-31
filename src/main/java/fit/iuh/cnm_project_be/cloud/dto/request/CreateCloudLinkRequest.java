package fit.iuh.cnm_project_be.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCloudLinkRequest {

    @NotBlank(message = "Link name is required")
    @Size(max = 255, message = "Link name must be at most 255 characters")
    private String name;

    @NotBlank(message = "URL is required")
    @Size(max = 2000, message = "URL must be at most 2000 characters")
    private String url;

    private UUID parentFolderId;
}
