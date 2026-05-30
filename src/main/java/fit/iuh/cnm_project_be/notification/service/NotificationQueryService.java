package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.notification.dto.NotificationPageResponse;
import fit.iuh.cnm_project_be.notification.dto.NotificationResponse;
import fit.iuh.cnm_project_be.notification.dto.UnreadCountResponse;
import fit.iuh.cnm_project_be.notification.entity.Notification;
import fit.iuh.cnm_project_be.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationRealtimePublisher realtimePublisher;

    @Transactional(readOnly = true)
    public NotificationPageResponse getMyNotifications(UUID currentUserId, String cursor, Integer limit, boolean unreadOnly) {
        int pageSize = normalizeLimit(limit);
        Instant now = Instant.now();
        Instant cursorInstant = parseCursor(cursor);
        List<Notification> notifications = cursorInstant == null
                ? notificationRepository.findCurrentPage(currentUserId, unreadOnly, now, PageRequest.of(0, pageSize + 1))
                : notificationRepository.findPageBeforeCursor(currentUserId, unreadOnly, now, cursorInstant, PageRequest.of(0, pageSize + 1));

        boolean hasMore = notifications.size() > pageSize;
        List<Notification> pageItems = hasMore ? notifications.subList(0, pageSize) : notifications;
        List<NotificationResponse> items = pageItems.stream()
                .map(notificationMapper::toResponse)
                .toList();
        String nextCursor = hasMore && !pageItems.isEmpty()
                ? pageItems.get(pageItems.size() - 1).getCreatedAt().toString()
                : null;

        return NotificationPageResponse.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID currentUserId) {
        return new UnreadCountResponse(notificationRepository.countUnread(currentUserId, Instant.now()));
    }

    @Transactional
    public NotificationResponse markAsRead(UUID currentUserId, UUID notificationId) {
        Notification notification = findOwnedNotification(currentUserId, notificationId);
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        NotificationResponse response = notificationMapper.toResponse(notification);
        long unreadCount = notificationRepository.countUnread(currentUserId, Instant.now());
        realtimePublisher.publishRead(currentUserId, response, unreadCount);
        return response;
    }

    @Transactional
    public UnreadCountResponse markAllAsRead(UUID currentUserId) {
        notificationRepository.markAllRead(currentUserId, Instant.now());
        realtimePublisher.publishReadAll(currentUserId, 0L);
        return new UnreadCountResponse(0L);
    }

    @Transactional
    public void deleteNotification(UUID currentUserId, UUID notificationId) {
        Notification notification = findOwnedNotification(currentUserId, notificationId);
        if (notification.getDeletedAt() == null) {
            notification.setDeletedAt(Instant.now());
            notificationRepository.save(notification);
        }
    }

    private Notification findOwnedNotification(UUID currentUserId, UUID notificationId) {
        return notificationRepository.findByIdAndRecipientIdAndDeletedAtIsNull(notificationId, currentUserId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException("Notification limit must be between 1 and 50");
        }
        return limit;
    }

    private Instant parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Notification cursor is invalid");
        }
    }
}
