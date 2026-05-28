package fit.iuh.cnm_project_be.admin.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AdminAlertResponse {
    private String type;
    private String severity;
    private String title;
    private String detail;
    private Instant createdAt;
}

