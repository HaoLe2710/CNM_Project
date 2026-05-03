package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SecurityHistoryItemResponse {
    private Long id;
    private String eventType;
    private String title;
    private String detail;
    private String deviceId;
    private String platform;
    private Instant createdAt;
}
