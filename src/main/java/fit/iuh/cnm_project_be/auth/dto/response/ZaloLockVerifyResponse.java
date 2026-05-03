package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZaloLockVerifyResponse {
    private boolean success;
    private int remainingAttempts;
    private long lockedUntilEpochMillis;
    private String message;
}
