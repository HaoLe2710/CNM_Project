package fit.iuh.cnm_project_be.admin.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AdminLogItemResponse {
    private String source;
    private Long id;
    private UUID userId;
    private String eventType;
    private String title;
    private String detail;
    private String metadata;
    private Instant createdAt;
}

