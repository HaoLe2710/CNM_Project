package fit.iuh.cnm_project_be.admin.dto.response;

import fit.iuh.cnm_project_be.system.enums.ReportTargetType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AdminReportItemResponse {
    private Long reportId;
    private ReportTargetType targetType;
    private String targetId;
    private String reason;
    private String actionTaken;
    private String reporterDisplayName;
    private String reporterUsername;
    private Instant createdAt;
    private Instant resolvedAt;
}

