package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationPolicyContext {
    private NotificationType notificationType;
    private NotificationTargetType targetType;
    private UUID actorId;
    private UUID recipientId;
    private UUID conversationId;
    private Long messageId;
    private UUID postId;
    private UUID commentId;
    private boolean directMention;
    private boolean replyToRecipientMessage;
    private boolean recipientDirectlyAffected;
    private boolean previewRequested;
    private Map<String, Object> metadata;
}
