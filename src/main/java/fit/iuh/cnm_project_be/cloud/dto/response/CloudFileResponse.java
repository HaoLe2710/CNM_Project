package fit.iuh.cnm_project_be.cloud.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CloudFileResponse {
    private UUID id;
    private UUID parentFolderId;
    private String name;
    private String originalFileName;
    private String mimeType;
    private String fileExtension;
    private Long fileSize;
    private String fileType;
    private String fileUrl;
    private String storageKey;
    private String manualContent;
    @JsonProperty("isFolder")
    private boolean isFolder;
    private Instant deletedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
