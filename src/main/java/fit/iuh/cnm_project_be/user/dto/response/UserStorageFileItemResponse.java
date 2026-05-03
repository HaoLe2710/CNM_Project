package fit.iuh.cnm_project_be.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserStorageFileItemResponse {
    private Long id;
    private String thumbnailUrl;
    private String name;
    private long sizeBytes;
    private double sizeMb;
    private String type;
    private Instant createdAt;
}
