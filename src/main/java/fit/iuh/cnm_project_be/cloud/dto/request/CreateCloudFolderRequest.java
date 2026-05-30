package fit.iuh.cnm_project_be.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCloudFolderRequest {

    @NotBlank(message = "Folder name is required")
    @Size(max = 255, message = "Folder name must be at most 255 characters")
    private String name;

    private UUID parentFolderId;
}
