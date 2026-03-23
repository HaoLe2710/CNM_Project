package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.CursorPageResponse;
import fit.iuh.cnm_project_be.message.dto.EditMessageRequest;
import fit.iuh.cnm_project_be.message.dto.MessageAttachmentPayload;
import fit.iuh.cnm_project_be.message.dto.MessageAttachmentResponse;
import fit.iuh.cnm_project_be.message.dto.MessageDeletedPayload;
import fit.iuh.cnm_project_be.message.dto.MessageReactionEventPayload;
import fit.iuh.cnm_project_be.message.dto.MessageReactionRequest;
import fit.iuh.cnm_project_be.message.dto.MessageReactionSummary;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.MessageStatusPayload;
import fit.iuh.cnm_project_be.message.dto.ReplyInfo;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageAttachment;
import fit.iuh.cnm_project_be.message.entity.MessageReaction;
import fit.iuh.cnm_project_be.message.entity.MessageStatus;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.enums.MessageReactionType;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.message.repository.MessageReactionRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.dto.ConversationStatusPayload;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final MessageUserStateRepository messageUserStateRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final S3MediaStorageService s3MediaStorageService;

    @Value("${app.message.unsend-window-minutes:15}")
    private long unsendWindowMinutes;

    @Transactional
    public MessageResponse sendMessage(UUID senderId, SendMessageRequest request) {
        Conversation conversation = getConversationOrThrow(request.getConversationId());
        ensureConversationMember(conversation.getId(), senderId);
        validatePayload(request);

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setContent(request.getContent());
        message.setMessageType(resolveMessageType(request));
        applyReplySnapshot(message, request);
        Message savedMessage = messageRepository.save(message);

        saveAttachments(savedMessage.getId(), request.getAttachments());
        // Delivery lifecycle stays in message_status for transport/status events.
        initializeStatuses(savedMessage.getId(), conversation.getId());
        // User-view state lives in message_user_states and drives visibility/seen reads.
        initializeUserStates(savedMessage.getId(), conversation.getId(), senderId);

        MessageUserState senderState = messageUserStateRepository.findByMessageIdAndUserId(savedMessage.getId(), senderId)
                .orElse(null);
        MessageResponse response = mapToResponse(savedMessage, senderId, Collections.emptyList(), Collections.emptyList(), senderState);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(),
                RealtimeEvent.of(RealtimeEventType.MESSAGE_CREATED, response));
        broadcastConversationUpdates(conversation.getId());

        return response;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(UUID conversationId, UUID currentUserId, String cursor, int size) {
        ensureConversationMember(conversationId, currentUserId);

        int pageSize = normalizePageSize(size);
        MessageCursor parsedCursor = parseCursor(cursor);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<Message> fetchedMessages = messageRepository.findVisibleMessages(
                conversationId,
                currentUserId,
                parsedCursor.createdAt(),
                parsedCursor.messageId(),
                pageable
        );

        boolean hasMore = fetchedMessages.size() > pageSize;
        List<Message> visibleMessages = hasMore
                ? new ArrayList<>(fetchedMessages.subList(0, pageSize))
                : fetchedMessages;

        List<MessageResponse> items = mapToResponses(visibleMessages, currentUserId);
        String nextCursor = hasMore ? encodeCursor(visibleMessages.get(visibleMessages.size() - 1)) : null;

        return CursorPageResponse.<MessageResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public MessageResponse editMessage(Long messageId, UUID actorId, EditMessageRequest request) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        ensureConversationMember(message.getConversationId(), actorId);
        if (!message.getSenderId().equals(actorId)) {
            throw new ForbiddenException("Only the sender can edit the message");
        }
        if (message.isDeleted()) {
            throw new BusinessException("Deleted messages cannot be edited");
        }
        if (message.getMessageType() != MessageType.TEXT) {
            throw new BusinessException("Only text messages can be edited");
        }

        String updatedContent = validateEditedContent(request, message.getContent());
        message.setContent(updatedContent);
        message.setEditedAt(Instant.now());
        Message savedMessage = messageRepository.save(message);

        List<MessageAttachment> attachments = messageAttachmentRepository.findByMessageIdIn(List.of(savedMessage.getId()));
        List<MessageReaction> reactions = messageReactionRepository.findByMessageIdIn(List.of(savedMessage.getId()));
        MessageResponse response = mapToResponse(savedMessage, actorId, attachments, reactions);

        messagingTemplate.convertAndSend("/topic/conversations/" + savedMessage.getConversationId(),
                RealtimeEvent.of(RealtimeEventType.MESSAGE_UPDATED, response));

        return response;
    }

    @Transactional
    public void deleteMessage(Long messageId, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        ensureConversationMember(message.getConversationId(), userId);
        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException("Only the sender can unsend the message");
        }
        if (message.isDeleted()) {
            throw new BusinessException("Message has already been deleted");
        }

        Instant recallDeadline = message.getCreatedAt().plus(Duration.ofMinutes(unsendWindowMinutes));
        if (Instant.now().isAfter(recallDeadline)) {
            throw new BusinessException("Message can no longer be unsent");
        }

        message.setDeletedAt(Instant.now());
        messageRepository.save(message);

        MessageDeletedPayload payload = new MessageDeletedPayload(
                message.getId(),
                message.getConversationId(),
                message.getDeletedAt()
        );
        messagingTemplate.convertAndSend("/topic/conversations/" + message.getConversationId(),
                RealtimeEvent.of(RealtimeEventType.MESSAGE_DELETED, payload));
        broadcastConversationUpdates(message.getConversationId());
    }

    @Transactional
    public void addOrUpdateReaction(Long messageId, UUID userId, MessageReactionRequest request) {
        Message message = getVisibleMessageOrThrow(messageId);
        ensureConversationMember(message.getConversationId(), userId);

        MessageReaction reaction = messageReactionRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> {
                    MessageReaction newReaction = new MessageReaction();
                    newReaction.setMessageId(messageId);
                    newReaction.setUserId(userId);
                    newReaction.setCreatedAt(Instant.now());
                    return newReaction;
                });

        reaction.setReactionType(request.getReactionType());
        reaction.setUpdatedAt(Instant.now());
        messageReactionRepository.save(reaction);

        broadcastReactionUpdate(message, userId, request.getReactionType());
    }

    @Transactional
    public void removeReaction(Long messageId, UUID userId) {
        Message message = getVisibleMessageOrThrow(messageId);
        ensureConversationMember(message.getConversationId(), userId);

        if (messageReactionRepository.findByMessageIdAndUserId(messageId, userId).isEmpty()) {
            return;
        }

        messageReactionRepository.deleteByMessageIdAndUserId(messageId, userId);
        broadcastReactionUpdate(message, userId, null);
    }

    // Hide-for-me is user-view state only and is stored in message_user_states.
    @Transactional
    public void hideMessage(Long messageId, UUID userId) {
        Message message = getVisibleMessageOrThrow(messageId);
        ensureConversationMember(message.getConversationId(), userId);

        Instant now = Instant.now();
        messageUserStateRepository.upsert(messageId, userId, state -> state.setHiddenAt(now));
    }

    @Transactional
    public void removeMessageForMe(Long messageId, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        ensureConversationMember(message.getConversationId(), userId);
        messageUserStateRepository.markDeletedForMe(messageId, userId, Instant.now());
    }

    @Transactional
    public void updateStatus(Long messageId, UUID userId, MessageDeliveryStatus status) {
        Message message = getVisibleMessageOrThrow(messageId);
        ensureConversationMember(message.getConversationId(), userId);

        if (status == MessageDeliveryStatus.SEEN) {
            handleSeenCompatibilityStatus(message, userId);
            return;
        }

        // Transport/status-topic compatibility lives in message_status for SENT/DELIVERED only.
        MessageStatus currentStatus = messageStatusRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> {
                    MessageStatus newStatus = new MessageStatus();
                    newStatus.setMessageId(messageId);
                    newStatus.setUserId(userId);
                    return newStatus;
                });

        currentStatus.setStatus(status);
        currentStatus.setUpdatedAt(Instant.now());
        messageStatusRepository.save(currentStatus);

        messagingTemplate.convertAndSend("/topic/messages/" + messageId + "/status",
                RealtimeEvent.of(RealtimeEventType.MESSAGE_STATUS_UPDATED,
                        new MessageStatusPayload(messageId, userId, status, currentStatus.getUpdatedAt())));
    }

    // Seen/read state is authoritative in message_user_states; no transport-state write is needed here.
    @Transactional
    public void markAsSeen(UUID conversationId, UUID userId) {
        ensureConversationMember(conversationId, userId);
        Instant now = Instant.now();
        messageUserStateRepository.markConversationAsSeen(conversationId, userId, now, now);

        messagingTemplate.convertAndSend("/topic/users/" + userId + "/conversations/status",
                RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED,
                        new ConversationStatusPayload(conversationId, "SEEN")));
        broadcastConversationUpdates(conversationId);
    }

    @Transactional(readOnly = true)
    public UploadAttachmentResponse uploadAttachment(UUID currentUserId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Attachment file is required");
        }
        return s3MediaStorageService.upload(currentUserId, file);
    }

    @Transactional(readOnly = true)
    public void assertConversationAccess(UUID conversationId, UUID userId) {
        ensureConversationMember(conversationId, userId);
    }

    private List<MessageResponse> mapToResponses(List<Message> messages, UUID currentUserId) {
        if (messages.isEmpty()) {
            return List.of();
        }

        List<Long> messageIds = messages.stream()
                .map(Message::getId)
                .toList();

        Map<Long, List<MessageAttachment>> attachmentsByMessageId = messageAttachmentRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(MessageAttachment::getMessageId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<MessageReaction>> reactionsByMessageId = messageReactionRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, MessageUserState> statesByMessageId = messageUserStateRepository.findByMessageIdInAndUserId(messageIds, currentUserId).stream()
                .collect(Collectors.toMap(MessageUserState::getMessageId, state -> state));

        return messages.stream()
                .map(message -> mapToResponse(
                        message,
                        currentUserId,
                        attachmentsByMessageId.getOrDefault(message.getId(), List.of()),
                        reactionsByMessageId.getOrDefault(message.getId(), List.of()),
                        statesByMessageId.get(message.getId())
                ))
                .toList();
    }

    private MessageResponse mapToResponse(
            Message message,
            UUID currentUserId,
            List<MessageAttachment> attachments,
            List<MessageReaction> reactions) {
        return mapToResponse(message, currentUserId, attachments, reactions, null);
    }

    private MessageResponse mapToResponse(
            Message message,
            UUID currentUserId,
            List<MessageAttachment> attachments,
            List<MessageReaction> reactions,
            MessageUserState state) {

        List<MessageAttachmentResponse> attachmentResponses = attachments.stream()
                .map(this::mapAttachment)
                .toList();

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .type(message.getMessageType())
                .replyTo(buildReplyInfo(message))
                .attachments(attachmentResponses)
                .reactions(summarizeReactions(reactions))
                .myReaction(findMyReaction(reactions, currentUserId))
                .seen(resolveSeen(state))
                .createdAt(message.getCreatedAt())
                .editedAt(message.getEditedAt())
                .build();
    }

    private MessageAttachmentResponse mapAttachment(MessageAttachment attachment) {
        return MessageAttachmentResponse.builder()
                .id(attachment.getId())
                .url(attachment.getFileUrl())
                .storageKey(attachment.getStorageKey())
                .fileName(attachment.getOriginalFileName())
                .contentType(attachment.getFileType())
                .fileSize(attachment.getFileSize())
                .type(attachment.getAttachmentType())
                .build();
    }

    private ReplyInfo buildReplyInfo(Message message) {
        if (message.getReplyToMessageId() == null) {
            return null;
        }

        return ReplyInfo.builder()
                .messageId(message.getReplyToMessageId())
                .senderId(message.getReplyToSenderId())
                .contentPreview(message.getReplyToContentPreview())
                .type(message.getReplyToType())
                .build();
    }

    private List<MessageReactionSummary> summarizeReactions(List<MessageReaction> reactions) {
        if (reactions.isEmpty()) {
            return List.of();
        }

        EnumMap<MessageReactionType, Long> counts = new EnumMap<>(MessageReactionType.class);
        reactions.forEach(reaction -> counts.merge(reaction.getReactionType(), 1L, Long::sum));

        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::ordinal)))
                .map(entry -> MessageReactionSummary.builder()
                        .type(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private MessageReactionType findMyReaction(List<MessageReaction> reactions, UUID currentUserId) {
        return reactions.stream()
                .filter(reaction -> reaction.getUserId().equals(currentUserId))
                .map(MessageReaction::getReactionType)
                .findFirst()
                .orElse(null);
    }

    private Boolean resolveSeen(MessageUserState state) {
        return state != null ? state.getSeenAt() != null : null;
    }

    private void handleSeenCompatibilityStatus(Message message, UUID userId) {
        Instant now = Instant.now();
        messageUserStateRepository.upsert(message.getId(), userId, state -> {
            if (state.getSeenAt() == null) {
                state.setSeenAt(now);
            }
        });

        messagingTemplate.convertAndSend("/topic/messages/" + message.getId() + "/status",
                RealtimeEvent.of(RealtimeEventType.MESSAGE_STATUS_UPDATED,
                        new MessageStatusPayload(message.getId(), userId, MessageDeliveryStatus.SEEN, now)));
        broadcastConversationUpdates(message.getConversationId());
    }

    private void validatePayload(SendMessageRequest request) {
        boolean hasContent = request.getContent() != null && !request.getContent().isBlank();
        boolean hasAttachments = request.getAttachments() != null && !request.getAttachments().isEmpty();

        if (!hasContent && !hasAttachments) {
            throw new BusinessException("Message must contain text or attachments");
        }
    }

    private String validateEditedContent(EditMessageRequest request, String existingContent) {
        if (request == null || request.getContent() == null) {
            throw new BusinessException("Message content is required");
        }

        String trimmedContent = request.getContent().trim();
        if (trimmedContent.isEmpty()) {
            throw new BusinessException("Message content must not be blank");
        }
        if (trimmedContent.equals(existingContent == null ? null : existingContent.trim())) {
            throw new BusinessException("Message content must be different from the current content");
        }

        return trimmedContent;
    }

    private MessageType resolveMessageType(SendMessageRequest request) {
        if (request.getMessageType() != null) {
            return request.getMessageType();
        }
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) {
            return MessageType.TEXT;
        }
        return request.getAttachments().get(0).getType();
    }

    private void applyReplySnapshot(Message message, SendMessageRequest request) {
        if (request.getReplyToMessageId() == null) {
            return;
        }

        Message repliedMessage = messageRepository.findByIdAndDeletedAtIsNull(request.getReplyToMessageId())
                .orElseThrow(() -> new NotFoundException("Replied message not found"));

        if (!repliedMessage.getConversationId().equals(request.getConversationId())) {
            throw new BusinessException("Reply target must belong to the same conversation");
        }

        message.setReplyToMessageId(repliedMessage.getId());
        message.setReplyToSenderId(repliedMessage.getSenderId());
        message.setReplyToType(repliedMessage.getMessageType());
        message.setReplyToContentPreview(buildContentPreview(repliedMessage.getContent()));
    }

    private String buildContentPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String trimmed = content.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    private void saveAttachments(Long messageId, List<MessageAttachmentPayload> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        attachments.forEach(payload -> {
            MessageAttachment attachment = new MessageAttachment();
            attachment.setMessageId(messageId);
            attachment.setFileUrl(payload.getUrl());
            attachment.setStorageKey(payload.getStorageKey());
            attachment.setOriginalFileName(payload.getFileName());
            attachment.setFileType(payload.getContentType());
            attachment.setAttachmentType(payload.getType());
            attachment.setFileSize(payload.getFileSize());
            messageAttachmentRepository.save(attachment);
        });
    }

    // Delivery lifecycle state remains separate from user-view state.
    private void initializeStatuses(Long messageId, UUID conversationId) {
        conversationMemberRepository.findByConversationId(conversationId).forEach(member -> {
            MessageStatus status = new MessageStatus();
            status.setMessageId(messageId);
            status.setUserId(member.getUserId());
            status.setStatus(MessageDeliveryStatus.SENT);
            status.setUpdatedAt(Instant.now());
            messageStatusRepository.save(status);
        });
    }

    // User-view state is the source of truth for seen/hidden/deleted-for-me.
    private void initializeUserStates(Long messageId, UUID conversationId, UUID senderId) {
        Instant now = Instant.now();
        conversationMemberRepository.findByConversationId(conversationId).forEach(member -> {
            MessageUserState state = new MessageUserState();
            state.setMessageId(messageId);
            state.setUserId(member.getUserId());
            if (member.getUserId().equals(senderId)) {
                state.setSeenAt(now);
            }
            messageUserStateRepository.save(state);
        });
    }

    private void ensureConversationMember(UUID conversationId, UUID userId) {
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("User does not belong to this conversation");
        }
    }

    private Conversation getConversationOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> !conversation.isDeleted())
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    private Message getVisibleMessageOrThrow(Long messageId) {
        return messageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
    }

    private void broadcastConversationUpdates(UUID conversationId) {
        conversationMemberRepository.findByConversationId(conversationId).forEach(member -> {
            ConversationResponse response = buildConversationResponse(conversationId, member.getUserId());
            messagingTemplate.convertAndSend("/topic/users/" + member.getUserId() + "/conversations",
                    RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED, response));
        });
    }

    private ConversationResponse buildConversationResponse(UUID conversationId, UUID userId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        List<Message> lastMessages = messageRepository.findVisibleMessages(conversationId, userId, null, null, PageRequest.of(0, 1));
        Message lastMessage = lastMessages.isEmpty() ? null : lastMessages.get(0);

        return ConversationResponse.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .type(String.valueOf(conversation.getType()))
                .lastMessage(lastMessage != null ? lastMessage.getContent() : "")
                .lastMessageTime(lastMessage != null ? lastMessage.getCreatedAt() : conversation.getCreatedAt())
                .unreadCount(messageUserStateRepository.countUnreadMessages(conversationId, userId))
                .build();
    }

    private void broadcastReactionUpdate(Message message, UUID actorUserId, MessageReactionType actorReaction) {
        List<MessageReaction> reactions = messageReactionRepository.findByMessageIdIn(List.of(message.getId()));
        MessageReactionEventPayload payload = MessageReactionEventPayload.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .actorUserId(actorUserId)
                .actorReaction(actorReaction)
                .summary(summarizeReactions(reactions))
                .build();

        messagingTemplate.convertAndSend("/topic/conversations/" + message.getConversationId(),
                RealtimeEvent.of(RealtimeEventType.MESSAGE_REACTION_UPDATED, payload));
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_MESSAGE_PAGE_SIZE);
    }

    private String encodeCursor(Message message) {
        String raw = message.getCreatedAt().toEpochMilli() + ":" + message.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private MessageCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new MessageCursor(null, null);
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 2) {
                throw new BusinessException("Invalid cursor");
            }

            Instant createdAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            Long messageId = Long.parseLong(parts[1]);
            return new MessageCursor(createdAt, messageId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid cursor");
        }
    }

    private record MessageCursor(Instant createdAt, Long messageId) {
    }
}
