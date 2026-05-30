package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.NotificationPolicyDenyReason;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotificationPolicyResult {
    private boolean inAppAllowed;
    private boolean pushAllowed;
    private boolean previewAllowed;
    private List<NotificationPolicyDenyReason> denyReasons;
}
