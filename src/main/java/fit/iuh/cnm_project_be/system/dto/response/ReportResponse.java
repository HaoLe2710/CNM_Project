package fit.iuh.cnm_project_be.system.dto.response;

import fit.iuh.cnm_project_be.system.enums.ReportTargetType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ReportResponse {
    private Long id;
    private ReportTargetType targetType;
    private String targetId;
    private String reason;
    private Instant createdAt;
}

