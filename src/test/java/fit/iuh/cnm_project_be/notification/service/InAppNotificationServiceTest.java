package fit.iuh.cnm_project_be.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.notification.dto.CreateNotificationCommand;
import fit.iuh.cnm_project_be.notification.dto.NotificationResponse;
import fit.iuh.cnm_project_be.notification.entity.Notification;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationRealtimePublisher realtimePublisher;

    private InAppNotificationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationMapper mapper = new NotificationMapper(objectMapper);
        service = new InAppNotificationService(notificationRepository, mapper, realtimePublisher, objectMapper);
    }

    @Test
    void createNotificationSuccess() {
        UUID recipientId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(notificationId);
            notification.setCreatedAt(Instant.parse("2026-05-28T00:00:00Z"));
            return notification;
        });

        NotificationResponse response = service.createNotification(CreateNotificationCommand.builder()
                .recipientId(recipientId)
                .type(NotificationType.SYSTEM_NOTICE)
                .title("Hello")
                .metadata(Map.of("source", "test"))
                .build());

        assertThat(response.getId()).isEqualTo(notificationId);
        assertThat(response.getRecipientId()).isEqualTo(recipientId);
        assertThat(response.getType()).isEqualTo(NotificationType.SYSTEM_NOTICE);
        assertThat(response.getMetadata()).containsEntry("source", "test");
        assertThat(response.isUnread()).isTrue();
    }

    @Test
    void createNotificationMissingRecipientThrowsError() {
        CreateNotificationCommand command = CreateNotificationCommand.builder()
                .type(NotificationType.SYSTEM_NOTICE)
                .title("Hello")
                .build();

        assertThatThrownBy(() -> service.createNotification(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recipient");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createNotificationMissingTitleThrowsError() {
        CreateNotificationCommand command = CreateNotificationCommand.builder()
                .recipientId(UUID.randomUUID())
                .type(NotificationType.SYSTEM_NOTICE)
                .title(" ")
                .build();

        assertThatThrownBy(() -> service.createNotification(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("title");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createNotificationWithDedupKeyDuplicateDoesNotCreateAgain() {
        UUID recipientId = UUID.randomUUID();
        Notification existing = notification(recipientId, "Existing");
        existing.setDedupKey("same-key");
        when(notificationRepository.findByDedupKey("same-key")).thenReturn(Optional.of(existing));

        NotificationResponse response = service.createNotificationIfAbsent(CreateNotificationCommand.builder()
                .recipientId(recipientId)
                .type(NotificationType.SYSTEM_NOTICE)
                .title("New")
                .dedupKey("same-key")
                .build());

        assertThat(response.getId()).isEqualTo(existing.getId());
        assertThat(response.getTitle()).isEqualTo("Existing");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createAndPublishPublishesRealtimeForNewNotification() {
        UUID recipientId = UUID.randomUUID();
        Notification saved = notification(recipientId, "Hello");
        when(notificationRepository.findByDedupKey("unique")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(notificationRepository.countUnread(any(UUID.class), any(Instant.class))).thenReturn(3L);

        NotificationResponse response = service.createAndPublish(CreateNotificationCommand.builder()
                .recipientId(recipientId)
                .type(NotificationType.SYSTEM_NOTICE)
                .title("Hello")
                .dedupKey("unique")
                .build());

        verify(realtimePublisher).publishCreated(recipientId, response, 3L);
    }

    private Notification notification(UUID recipientId, String title) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(recipientId);
        notification.setType(NotificationType.SYSTEM_NOTICE);
        notification.setTitle(title);
        notification.setCreatedAt(Instant.parse("2026-05-28T00:00:00Z"));
        return notification;
    }
}
