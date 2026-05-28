package fit.iuh.cnm_project_be.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationRealtimePayload {
    private String eventType;
    private UUID notificationId;
    private NotificationResponse notification;
    private long unreadCount;
    private Instant occurredAt;
}
