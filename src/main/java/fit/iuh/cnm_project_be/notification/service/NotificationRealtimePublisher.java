package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.NotificationRealtimePayload;
import fit.iuh.cnm_project_be.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationRealtimePublisher {

    public static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";
    public static final String NOTIFICATION_READ = "NOTIFICATION_READ";
    public static final String NOTIFICATIONS_READ_ALL = "NOTIFICATIONS_READ_ALL";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishCreated(UUID userId, NotificationResponse notification, long unreadCount) {
        publish(userId, NotificationRealtimePayload.builder()
                .eventType(NOTIFICATION_CREATED)
                .notificationId(notification.getId())
                .notification(notification)
                .unreadCount(unreadCount)
                .occurredAt(Instant.now())
                .build());
    }

    public void publishRead(UUID userId, NotificationResponse notification, long unreadCount) {
        publish(userId, NotificationRealtimePayload.builder()
                .eventType(NOTIFICATION_READ)
                .notificationId(notification.getId())
                .notification(notification)
                .unreadCount(unreadCount)
                .occurredAt(Instant.now())
                .build());
    }

    public void publishReadAll(UUID userId, long unreadCount) {
        publish(userId, NotificationRealtimePayload.builder()
                .eventType(NOTIFICATIONS_READ_ALL)
                .unreadCount(unreadCount)
                .occurredAt(Instant.now())
                .build());
    }

    private void publish(UUID userId, NotificationRealtimePayload payload) {
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", payload);
    }
}
