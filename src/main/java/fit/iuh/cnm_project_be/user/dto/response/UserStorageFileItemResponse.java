package fit.iuh.cnm_project_be.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserStorageFileItemResponse {
    private Long id;
    private Long messageId;
    private UUID conversationId;
    private String conversationName;
    private String fileUrl;
    private String thumbnailUrl;
    private String name;
    private long sizeBytes;
    private double sizeMb;
    private String type;
    private Instant createdAt;
}
