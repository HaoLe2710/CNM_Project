package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecurityCheckResponse {
    private String id;
    private String title;
    private boolean passed;
    private String detail;
}
