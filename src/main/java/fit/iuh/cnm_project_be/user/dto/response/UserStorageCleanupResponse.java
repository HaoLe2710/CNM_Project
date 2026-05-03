package fit.iuh.cnm_project_be.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStorageCleanupResponse {
    private String target;
    private boolean serverSideApplied;
    private String action;
    private String message;
}
