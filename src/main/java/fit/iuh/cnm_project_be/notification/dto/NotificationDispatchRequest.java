package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationDispatchRequest {
    private NotificationType type;
    private NotificationTargetType targetType;
    private UUID actorId;
    private Collection<UUID> explicitRecipientIds;
    private UUID targetId;
    private UUID conversationId;
    private Long messageId;
    private UUID postId;
    private UUID commentId;
    private String title;
    private String body;
    private String genericBody;
    private Map<String, Object> metadata;
    private String dedupKeyPrefix;
    private boolean directMention;
    private boolean replyToRecipientMessage;
    private boolean recipientDirectlyAffected;
    private boolean forceNoPush;
    private boolean forceNoInApp;
}
