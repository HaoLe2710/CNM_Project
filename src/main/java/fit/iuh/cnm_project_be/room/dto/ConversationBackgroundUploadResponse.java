package fit.iuh.cnm_project_be.room.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationBackgroundUploadResponse {
    private String url;
    private String storageKey;
    private String fileName;
    private String contentType;
    private Long fileSize;
}
