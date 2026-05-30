package fit.iuh.cnm_project_be.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PushSendResult {
    private int requestedUserCount;
    private int targetTokenCount;
    private int successCount;
    private int failureCount;
    private int disabledTokenCount;
    private List<UUID> failedTokenIds;
    private List<String> providerErrors;

    public boolean hasSuccess() {
        return successCount > 0;
    }
}
