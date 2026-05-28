package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private UUID recipientId;
    private UUID actorId;
    private NotificationType type;
    private String title;
    private String body;
    private NotificationTargetType targetType;
    private UUID targetId;
    private UUID conversationId;
    private Long messageId;
    private UUID postId;
    private UUID commentId;
    private Map<String, Object> metadata;
    private Instant readAt;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean unread;
}
