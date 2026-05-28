package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.NotificationPolicyDenyReason;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationDispatchResult {
    private int candidateRecipientCount;
    private int policyAllowedInAppCount;
    private int policyAllowedPushCount;
    private int createdNotificationCount;
    private int pushSuccessCount;
    private int pushFailureCount;
    private Map<UUID, List<NotificationPolicyDenyReason>> deniedRecipients;
    private List<UUID> createdNotificationIds;
    private List<String> errors;

    public boolean hasPushSuccess() {
        return pushSuccessCount > 0;
    }

    public boolean wasPolicyAllowedFor(UUID recipientId) {
        return deniedRecipients == null || !deniedRecipients.containsKey(recipientId);
    }
}
