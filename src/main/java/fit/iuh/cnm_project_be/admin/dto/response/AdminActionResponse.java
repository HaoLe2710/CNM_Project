package fit.iuh.cnm_project_be.admin.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AdminActionResponse {
    private boolean success;
    private String message;
    private Instant processedAt;
}

