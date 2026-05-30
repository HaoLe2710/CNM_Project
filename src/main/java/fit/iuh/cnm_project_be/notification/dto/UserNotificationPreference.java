package fit.iuh.cnm_project_be.notification.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class UserNotificationPreference {
    private boolean notificationsEnabled;
    private boolean chatNotificationsEnabled;
    private boolean callNotificationsEnabled;
    private boolean socialNotificationsEnabled;
    private boolean pushEnabled;
    private boolean previewEnabled;
}
