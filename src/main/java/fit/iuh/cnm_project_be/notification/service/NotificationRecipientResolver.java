package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.notification.dto.ResolvedNotificationRecipient;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationRecipientResolver {

    private final ConversationMemberRepository conversationMemberRepository;

    @Transactional(readOnly = true)
    public List<UUID> resolvePrivateMessageRecipients(Message message, Conversation conversation) {
        if (message == null || conversation == null || conversation.getType() != ConversationType.PRIVATE) {
            return List.of();
        }
        return conversationMemberRepository.findByConversationId(conversation.getId()).stream()
                .map(ConversationMember::getUserId)
                .filter(userId -> !userId.equals(message.getSenderId()))
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> resolveGroupMessageRecipients(Message message, Conversation conversation) {
        if (message == null || conversation == null || conversation.getType() != ConversationType.GROUP) {
            return List.of();
        }
        return conversationMemberRepository.findByConversationId(conversation.getId()).stream()
                .map(ConversationMember::getUserId)
                .filter(userId -> !userId.equals(message.getSenderId()))
                .distinct()
                .toList();
    }

    public List<UUID> resolveGroupMentionRecipients(Message message, Collection<UUID> mentionedUserIds) {
        if (message == null || mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return List.of();
        }
        return mentionedUserIds.stream()
                .filter(userId -> userId != null && !userId.equals(message.getSenderId()))
                .distinct()
                .toList();
    }

    public List<UUID> resolveReplyRecipients(Message message) {
        if (message == null || message.getReplyToSenderId() == null || message.getReplyToSenderId().equals(message.getSenderId())) {
            return List.of();
        }
        return List.of(message.getReplyToSenderId());
    }

    public List<UUID> resolveReactionRecipients(Message message, UUID reactionActorId) {
        if (message == null || message.getSenderId() == null || message.getSenderId().equals(reactionActorId)) {
            return List.of();
        }
        return List.of(message.getSenderId());
    }

    @Transactional(readOnly = true)
    public List<UUID> resolveGroupCallStartedRecipients(UUID conversationId, UUID initiatorId) {
        if (conversationId == null) {
            return List.of();
        }
        return conversationMemberRepository.findByConversationId(conversationId).stream()
                .map(ConversationMember::getUserId)
                .filter(userId -> !userId.equals(initiatorId))
                .distinct()
                .toList();
    }

    public List<ResolvedNotificationRecipient> resolveMessageRecipients(
            Message message,
            Conversation conversation,
            Collection<UUID> mentionedUserIds) {
        Map<UUID, ResolvedNotificationRecipient> recipients = new LinkedHashMap<>();

        resolveGroupMentionRecipients(message, mentionedUserIds).forEach(userId ->
                upsertRecipientWithPriority(recipients, ResolvedNotificationRecipient.builder()
                        .userId(userId)
                        .type(NotificationType.GROUP_MENTION)
                        .directMention(true)
                        .build()));

        resolveReplyRecipients(message).forEach(userId ->
                upsertRecipientWithPriority(recipients, ResolvedNotificationRecipient.builder()
                        .userId(userId)
                        .type(NotificationType.REPLY_TO_MY_MESSAGE)
                        .replyToRecipientMessage(true)
                        .build()));

        List<UUID> baseRecipients = conversation.getType() == ConversationType.PRIVATE
                ? resolvePrivateMessageRecipients(message, conversation)
                : resolveGroupMessageRecipients(message, conversation);
        NotificationType baseType = conversation.getType() == ConversationType.PRIVATE
                ? NotificationType.NEW_PRIVATE_MESSAGE
                : NotificationType.NEW_GROUP_MESSAGE;
        baseRecipients.forEach(userId ->
                upsertRecipientWithPriority(recipients, ResolvedNotificationRecipient.builder()
                        .userId(userId)
                        .type(baseType)
                        .build()));

        return List.copyOf(recipients.values());
    }

    private void upsertRecipientWithPriority(
            Map<UUID, ResolvedNotificationRecipient> recipients,
            ResolvedNotificationRecipient candidate) {
        if (candidate == null || candidate.getUserId() == null) {
            return;
        }
        recipients.merge(candidate.getUserId(), candidate, this::mergeByPriority);
    }

    private ResolvedNotificationRecipient mergeByPriority(
            ResolvedNotificationRecipient existing,
            ResolvedNotificationRecipient candidate) {
        ResolvedNotificationRecipient preferred = priorityOf(existing.getType()) <= priorityOf(candidate.getType())
                ? existing
                : candidate;

        return ResolvedNotificationRecipient.builder()
                .userId(preferred.getUserId())
                .type(preferred.getType())
                .directMention(existing.isDirectMention() || candidate.isDirectMention())
                .replyToRecipientMessage(existing.isReplyToRecipientMessage() || candidate.isReplyToRecipientMessage())
                .recipientDirectlyAffected(existing.isRecipientDirectlyAffected() || candidate.isRecipientDirectlyAffected())
                .build();
    }

    private int priorityOf(NotificationType type) {
        if (type == NotificationType.GROUP_MENTION) {
            return 1;
        }
        if (type == NotificationType.REPLY_TO_MY_MESSAGE) {
            return 2;
        }
        if (type == NotificationType.NEW_PRIVATE_MESSAGE) {
            return 3;
        }
        if (type == NotificationType.NEW_GROUP_MESSAGE) {
            return 4;
        }
        return Integer.MAX_VALUE;
    }
}
