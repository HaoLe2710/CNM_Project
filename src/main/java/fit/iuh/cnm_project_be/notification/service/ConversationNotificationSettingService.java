package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.notification.dto.ConversationNotificationSettingRequest;
import fit.iuh.cnm_project_be.notification.dto.ConversationNotificationSettingResponse;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationNotificationSettingService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationUserSettingRepository conversationUserSettingRepository;
    private final UserService userService;
    private final Clock notificationPolicyClock;

    @Transactional(readOnly = true)
    public ConversationNotificationSettingResponse getMySetting(UUID conversationId) {
        UUID currentUserId = userService.getCurrentUserId();
        Conversation conversation = getActiveConversationOrThrow(conversationId);
        ensureMember(conversation.getId(), currentUserId);
        return toResponse(findSetting(conversation.getId(), currentUserId).orElse(null), conversation.getId(), currentUserId);
    }

    @Transactional
    public ConversationNotificationSettingResponse updateMySetting(
            UUID conversationId,
            ConversationNotificationSettingRequest request) {
        UUID currentUserId = userService.getCurrentUserId();
        Conversation conversation = getActiveConversationOrThrow(conversationId);
        ensureMember(conversation.getId(), currentUserId);

        ConversationUserSetting setting = findSetting(conversation.getId(), currentUserId)
                .orElseGet(() -> {
                    ConversationUserSetting created = new ConversationUserSetting();
                    created.setConversationId(conversation.getId());
                    created.setUserId(currentUserId);
                    return created;
                });

        ConversationNotificationLevel level = request != null && request.getNotificationLevel() != null
                ? request.getNotificationLevel()
                : resolveNotificationLevel(setting);
        Instant now = now();
        Instant requestedMutedUntil = request != null ? request.getMutedUntil() : null;
        Instant normalizedMutedUntil = requestedMutedUntil != null && requestedMutedUntil.isAfter(now)
                ? requestedMutedUntil
                : null;

        setting.setNotificationLevel(level);
        setting.setMutedUntil(normalizedMutedUntil);

        boolean mutedByLevel = level == ConversationNotificationLevel.NONE;
        boolean mutedByTime = normalizedMutedUntil != null;
        if (mutedByLevel || mutedByTime) {
            if (setting.getLastMutedAt() == null || setting.getMutedAt() == null) {
                setting.setLastMutedAt(now);
            }
            setting.setMutedAt(now);
        } else {
            setting.setMutedAt(null);
        }

        ConversationUserSetting saved = conversationUserSettingRepository.save(setting);
        return toResponse(saved, conversation.getId(), currentUserId);
    }

    @Transactional(readOnly = true)
    public ConversationNotificationLevel getLevelForUser(UUID userId, UUID conversationId) {
        return findSetting(conversationId, userId)
                .map(this::resolveNotificationLevel)
                .orElse(ConversationNotificationLevel.ALL);
    }

    @Transactional(readOnly = true)
    public boolean isConversationMutedForUser(UUID userId, UUID conversationId, Instant now) {
        return findSetting(conversationId, userId)
                .map(setting -> isMuted(setting, now))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<ConversationUserSetting> findSettingForPolicy(UUID conversationId, UUID userId) {
        return findSetting(conversationId, userId);
    }

    public ConversationNotificationLevel resolveNotificationLevel(ConversationUserSetting setting) {
        return setting != null && setting.getNotificationLevel() != null
                ? setting.getNotificationLevel()
                : ConversationNotificationLevel.ALL;
    }

    public boolean isMuted(ConversationUserSetting setting, Instant now) {
        if (setting == null) {
            return false;
        }
        ConversationNotificationLevel level = resolveNotificationLevel(setting);
        if (level == ConversationNotificationLevel.NONE) {
            return true;
        }
        if (setting.getMutedUntil() != null) {
            return setting.getMutedUntil().isAfter(now);
        }
        return setting.getMutedAt() != null;
    }

    private ConversationNotificationSettingResponse toResponse(
            ConversationUserSetting setting,
            UUID conversationId,
            UUID userId) {
        Instant now = now();
        return ConversationNotificationSettingResponse.builder()
                .conversationId(conversationId)
                .userId(userId)
                .notificationLevel(resolveNotificationLevel(setting))
                .mutedUntil(setting != null ? setting.getMutedUntil() : null)
                .muted(isMuted(setting, now))
                .lastMutedAt(setting != null ? setting.getLastMutedAt() : null)
                .updatedAt(setting != null ? setting.getUpdatedAt() : null)
                .build();
    }

    private Optional<ConversationUserSetting> findSetting(UUID conversationId, UUID userId) {
        if (conversationId == null || userId == null) {
            return Optional.empty();
        }
        return conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId);
    }

    private Conversation getActiveConversationOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> !conversation.isDeleted())
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    private void ensureMember(UUID conversationId, UUID userId) {
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("User does not belong to this conversation");
        }
    }

    private Instant now() {
        return Instant.now(notificationPolicyClock);
    }
}
