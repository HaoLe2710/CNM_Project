package fit.iuh.cnm_project_be.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.notification.dto.NotificationPageResponse;
import fit.iuh.cnm_project_be.notification.dto.NotificationResponse;
import fit.iuh.cnm_project_be.notification.entity.Notification;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationRealtimePublisher realtimePublisher;

    private NotificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationQueryService(
                notificationRepository,
                new NotificationMapper(new ObjectMapper()),
                realtimePublisher
        );
    }

    @Test
    void getUnreadCountReturnsOnlyUnread() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countUnread(eq(userId), any(Instant.class))).thenReturn(5L);

        assertThat(service.getUnreadCount(userId).unreadCount()).isEqualTo(5L);
    }

    @Test
    void getMyNotificationsReturnsPageAndNextCursor() {
        UUID userId = UUID.randomUUID();
        Notification newest = notification(userId, "Newest", Instant.parse("2026-05-28T02:00:00Z"));
        Notification older = notification(userId, "Older", Instant.parse("2026-05-28T01:00:00Z"));
        when(notificationRepository.findCurrentPage(eq(userId), eq(false), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(newest, older));

        NotificationPageResponse response = service.getMyNotifications(userId, null, 1, false);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("Newest");
        assertThat(response.isHasMore()).isTrue();
        assertThat(response.getNextCursor()).isEqualTo("2026-05-28T02:00:00Z");
    }

    @Test
    void getMyNotificationsRejectsInvalidLimit() {
        assertThatThrownBy(() -> service.getMyNotifications(UUID.randomUUID(), null, 51, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void markAsReadSuccess() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId, "Unread", Instant.now());
        when(notificationRepository.findByIdAndRecipientIdAndDeletedAtIsNull(notification.getId(), userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);
        when(notificationRepository.countUnread(eq(userId), any(Instant.class))).thenReturn(0L);

        NotificationResponse response = service.markAsRead(userId, notification.getId());

        assertThat(response.getReadAt()).isNotNull();
        assertThat(response.isUnread()).isFalse();
        verify(realtimePublisher).publishRead(eq(userId), any(NotificationResponse.class), eq(0L));
    }

    @Test
    void markAsReadIsIdempotent() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId, "Read", Instant.now());
        notification.setReadAt(Instant.parse("2026-05-28T03:00:00Z"));
        when(notificationRepository.findByIdAndRecipientIdAndDeletedAtIsNull(notification.getId(), userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.countUnread(eq(userId), any(Instant.class))).thenReturn(0L);

        NotificationResponse response = service.markAsRead(userId, notification.getId());

        assertThat(response.getReadAt()).isEqualTo(Instant.parse("2026-05-28T03:00:00Z"));
    }

    @Test
    void markAsReadOtherUserNotificationNotAllowed() {
        when(notificationRepository.findByIdAndRecipientIdAndDeletedAtIsNull(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAllAsReadSuccess() {
        UUID userId = UUID.randomUUID();

        assertThat(service.markAllAsRead(userId).unreadCount()).isZero();

        verify(notificationRepository).markAllRead(eq(userId), any(Instant.class));
        verify(realtimePublisher).publishReadAll(userId, 0L);
    }

    @Test
    void deleteNotificationSuccess() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId, "Hide", Instant.now());
        when(notificationRepository.findByIdAndRecipientIdAndDeletedAtIsNull(notification.getId(), userId))
                .thenReturn(Optional.of(notification));

        service.deleteNotification(userId, notification.getId());

        assertThat(notification.getDeletedAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    private Notification notification(UUID recipientId, String title, Instant createdAt) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(recipientId);
        notification.setType(NotificationType.SYSTEM_NOTICE);
        notification.setTitle(title);
        notification.setCreatedAt(createdAt);
        return notification;
    }
}
