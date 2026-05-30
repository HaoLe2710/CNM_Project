package fit.iuh.cnm_project_be.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminResolveReportRequest {
    @NotBlank
    private String action;
    private String note;
    private Integer banHours;
}

