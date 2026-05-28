package fit.iuh.cnm_project_be.system.dto.request;

import fit.iuh.cnm_project_be.system.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReportRequest {
    @NotNull
    private ReportTargetType targetType;
    @NotBlank
    private String targetId;
    @NotBlank
    private String reason;
}

