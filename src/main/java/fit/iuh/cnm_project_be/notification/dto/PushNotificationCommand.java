package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class PushNotificationCommand {
    private String title;
    private String body;
    private Map<String, Object> data;
    private UUID notificationId;
    private NotificationType notificationType;
    private NotificationTargetType targetType;
    private UUID targetId;
    private UUID conversationId;
    private Long messageId;
    private UUID postId;
    private UUID commentId;
    private UUID actorId;
}
