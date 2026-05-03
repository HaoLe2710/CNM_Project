package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecurityIssueResponse {
    private String code;
    private String severity;
    private String title;
    private String description;
    private String actionKey;
    private String actionLabel;
}
