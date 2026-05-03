package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZaloLockChallengeResponse {
    private String challengeToken;
    private long expiresInSeconds;
    private int remainingAttempts;
    private long lockedUntilEpochMillis;
}
