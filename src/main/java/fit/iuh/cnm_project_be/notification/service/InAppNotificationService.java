package fit.iuh.cnm_project_be.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.notification.dto.CreateNotificationCommand;
import fit.iuh.cnm_project_be.notification.dto.NotificationResponse;
import fit.iuh.cnm_project_be.notification.entity.Notification;
import fit.iuh.cnm_project_be.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationRealtimePublisher realtimePublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationResponse createNotification(CreateNotificationCommand command) {
        return notificationMapper.toResponse(saveNotification(command));
    }

    @Transactional
    public NotificationResponse createNotificationIfAbsent(CreateNotificationCommand command) {
        if (command.getDedupKey() != null && !command.getDedupKey().isBlank()) {
            return notificationRepository.findByDedupKey(command.getDedupKey())
                    .map(notificationMapper::toResponse)
                    .orElseGet(() -> createWithDuplicateProtection(command));
        }
        return createNotification(command);
    }

    @Transactional
    public NotificationResponse createAndPublish(CreateNotificationCommand command) {
        validate(command);
        if (command.getDedupKey() != null && !command.getDedupKey().isBlank()) {
            var existing = notificationRepository.findByDedupKey(command.getDedupKey().trim());
            if (existing.isPresent()) {
                return notificationMapper.toResponse(existing.get());
            }
        }
        NotificationResponse response = createNotificationIfAbsent(command);
        long unreadCount = notificationRepository.countUnread(command.getRecipientId(), Instant.now());
        realtimePublisher.publishCreated(command.getRecipientId(), response, unreadCount);
        return response;
    }

    private NotificationResponse createWithDuplicateProtection(CreateNotificationCommand command) {
        try {
            return createNotification(command);
        } catch (DataIntegrityViolationException ex) {
            return notificationRepository.findByDedupKey(command.getDedupKey())
                    .map(notificationMapper::toResponse)
                    .orElseThrow(() -> ex);
        }
    }

    private Notification saveNotification(CreateNotificationCommand command) {
        validate(command);
        Notification notification = new Notification();
        notification.setRecipientId(command.getRecipientId());
        notification.setActorId(command.getActorId());
        notification.setType(command.getType());
        notification.setTitle(command.getTitle().trim());
        notification.setBody(command.getBody());
        notification.setTargetType(command.getTargetType());
        notification.setTargetId(command.getTargetId());
        notification.setConversationId(command.getConversationId());
        notification.setMessageId(command.getMessageId());
        notification.setPostId(command.getPostId());
        notification.setCommentId(command.getCommentId());
        notification.setMetadataJson(serializeMetadata(command));
        notification.setDedupKey(normalizeDedupKey(command.getDedupKey()));
        notification.setExpiresAt(command.getExpiresAt());
        return notificationRepository.save(notification);
    }

    private void validate(CreateNotificationCommand command) {
        if (command == null) {
            throw new BusinessException("Notification command is required");
        }
        if (command.getRecipientId() == null) {
            throw new BusinessException("Notification recipient is required");
        }
        if (command.getType() == null) {
            throw new BusinessException("Notification type is required");
        }
        if (command.getTitle() == null || command.getTitle().isBlank()) {
            throw new BusinessException("Notification title is required");
        }
    }

    private String serializeMetadata(CreateNotificationCommand command) {
        if (command.getMetadata() == null || command.getMetadata().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(command.getMetadata());
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Notification metadata is invalid");
        }
    }

    private String normalizeDedupKey(String dedupKey) {
        return dedupKey == null || dedupKey.isBlank() ? null : dedupKey.trim();
    }
}
