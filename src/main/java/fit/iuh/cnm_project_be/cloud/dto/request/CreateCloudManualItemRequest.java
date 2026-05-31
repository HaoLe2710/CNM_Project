package fit.iuh.cnm_project_be.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCloudManualItemRequest {

    @NotBlank(message = "Manual item name is required")
    @Size(max = 255, message = "Manual item name must be at most 255 characters")
    private String name;

    @Size(max = 4000, message = "Content must be at most 4000 characters")
    private String content;

    private UUID parentFolderId;
}
