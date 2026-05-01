package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckEmailResponse {
    private String email;
    private boolean exists;
    private String nextStep;
    private String message;
}
