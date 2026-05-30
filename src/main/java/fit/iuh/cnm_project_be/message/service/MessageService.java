package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.CursorPageResponse;
import fit.iuh.cnm_project_be.message.dto.EditMessageRequest;
import fit.iuh.cnm_project_be.message.dto.MessageAttachmentPayload;
import fit.iuh.cnm_project_be.message.dto.MessageAttachmentResponse;
import fit.iuh.cnm_project_be.message.dto.MessageContextResponse;
import fit.iuh.cnm_project_be.message.dto.MessageDeletedPayload;
import fit.iuh.cnm_project_be.message.dto.MessageReactionEventPayload;
import fit.iuh.cnm_project_be.message.dto.MessageReactionRequest;
import fit.iuh.cnm_project_be.message.dto.MessageReactionSummary;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.MessageStatusPayload;
import fit.iuh.cnm_project_be.message.dto.ReplyInfo;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.dto.TypingRealtimePayload;
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
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.ResolvedNotificationRecipient;
import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationContentBuilder;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.notification.service.NotificationRecipientResolver;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.dto.ConversationMemberResponse;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.dto.ConversationStatusPayload;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.GroupConversationLabel;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageService {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;
    private static final long MAX_AUDIO_DURATION_MS = 300_000L;
    private static final int MIN_WAVEFORM_SAMPLES = 16;
    private static final int MAX_WAVEFORM_SAMPLES = 128;
    private static final int MAX_AUDIO_FORMAT_LENGTH = 32;
    private static final String GROUP_SYSTEM_PREFIX = "[[GROUP_SYSTEM]]";
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9._])@([A-Za-z0-9._]+)");
    private static final Pattern ABSOLUTE_URL_PATTERN = Pattern.compile("^(?i)https?://\\S+$");
    private static final String SIMPLE_JSON_STRING_FIELD_REGEX = "\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";

    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final MessageUserStateRepository messageUserStateRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationUserSettingRepository conversationUserSettingRepository;
    private final UserProfileRepository userProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final S3MediaStorageService s3MediaStorageService;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationRecipientResolver notificationRecipientResolver;
    private final NotificationContentBuilder notificationContentBuilder;

    @Value("${app.message.unsend-window-minutes:15}")
    private long unsendWindowMinutes;

    @Transactional
    public MessageResponse sendMessage(UUID senderId, SendMessageRequest request) {
        Conversation conversation = getConversationOrThrow(request.getConversationId());
        ensureConversationMember(conversation.getId(), senderId);
        validatePayload(request);
        String normalizedContent = normalizeNullableText(request.getContent());
        String resolvedOriginalLinkUrl = resolveOriginalLinkUrl(normalizedContent, request.getOriginalLinkUrl());

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setContent(normalizedContent);
        message.setOriginalLinkUrl(resolvedOriginalLinkUrl);
        message.setMessageType(resolveMessageType(request));
        applyReplySnapshot(message, request);
        Message savedMessage = messageRepository.save(message);

        saveAttachments(savedMessage.getId(), request.getAttachments());
        // Delivery lifecycle stays in message_status for transport/status events.
        initializeStatuses(savedMessage.getId(), conversation.getId());
        // User-view state lives in message_user_states and drives visibility/seen reads.
        initializeUserStates(savedMessage.getId(), conversation.getId(), senderId);

        List<MessageAttachment> attachments = messageAttachmentRepository.findByMessageIdIn(List.of(savedMessage.getId()));
        MessageUserState senderState = messageUserStateRepository.findByMessageIdAndUserId(savedMessage.getId(), senderId)
                .orElse(null);
        MessageResponse response = mapToResponse(savedMessage, senderId, attachments, Collections.emptyList(), senderState);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(),
                RealtimeEvent.of(RealtimeEventType.MESSAGE_CREATED, response));
        broadcastConversationUpdatesForNewMessage(conversation, savedMessage);
        safeDispatchMessageNotifications(conversation, savedMessage);

        return response;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(UUID conversationId, UUID currentUserId, String cursor, int size) {
        getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, currentUserId);

        int pageSize = normalizePageSize(size);
        MessageCursor parsedCursor = parseCursor(cursor);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<Message> fetchedMessages = loadVisibleMessages(conversationId, currentUserId, parsedCursor, pageable);

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

    @Transactional(readOnly = true)
    public MessageContextResponse getMessageContext(UUID conversationId, Long messageId, UUID currentUserId, int range) {
        getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, currentUserId);

        int normalizedRange = normalizeContextRange(range);
        int sideWindow = Math.max(1, normalizedRange / 2);

        Message anchorMessage = getVisibleMessageForUserOrThrow(messageId, currentUserId);
        if (!anchorMessage.getConversationId().equals(conversationId)) {
            throw new BusinessException("Message does not belong to the requested conversation");
        }

        List<Message> olderMessages = messageRepository.findVisibleMessagesOlderThanAnchor(
                conversationId,
                currentUserId,
                anchorMessage.getCreatedAt(),
                anchorMessage.getId(),
                PageRequest.of(0, sideWindow + 1)
        );
        boolean hasOlder = olderMessages.size() > sideWindow;
        if (hasOlder) {
            olderMessages = new ArrayList<>(olderMessages.subList(0, sideWindow));
        }
        Collections.reverse(olderMessages);

        List<Message> newerMessages = messageRepository.findVisibleMessagesNewerThanAnchor(
                conversationId,
                currentUserId,
                anchorMessage.getCreatedAt(),
                anchorMessage.getId(),
                PageRequest.of(0, sideWindow + 1)
        );
        boolean hasNewer = newerMessages.size() > sideWindow;
        if (hasNewer) {
            newerMessages = new ArrayList<>(newerMessages.subList(0, sideWindow));
        }

        List<Message> contextMessages = new ArrayList<>(olderMessages.size() + 1 + newerMessages.size());
        contextMessages.addAll(olderMessages);
        contextMessages.add(anchorMessage);
        contextMessages.addAll(newerMessages);

        List<MessageResponse> items = mapToResponses(contextMessages, currentUserId);
        Message latestVisibleMessage = resolveLatestVisibleMessage(conversationId, currentUserId);

        return MessageContextResponse.builder()
                .anchorMessageId(anchorMessage.getId())
                .items(items)
                .hasOlder(hasOlder)
                .hasNewer(hasNewer)
                .latestMessageId(latestVisibleMessage != null ? latestVisibleMessage.getId() : null)
                .latestCursor(latestVisibleMessage != null ? encodeCursor(latestVisibleMessage) : null)
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

        String updatedContent = validateEditedContent(request, message.getContent(), message.getOriginalLinkUrl());
        String resolvedOriginalLinkUrl = resolveOriginalLinkUrl(updatedContent, request.getOriginalLinkUrl());
        message.setContent(updatedContent);
        message.setOriginalLinkUrl(resolvedOriginalLinkUrl);
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
    public MessageResponse updatePinState(Long messageId, UUID actorId, boolean pinned) {
        Message message = getVisibleMessageOrThrow(messageId);
        Conversation conversation = getConversationOrThrow(message.getConversationId());
        ConversationMember actorMember = getConversationMemberOrThrow(conversation.getId(), actorId);

        ensureCanPinMessages(conversation, actorMember);

        message.setPinnedAt(pinned ? Instant.now() : null);
        Message savedMessage = messageRepository.save(message);

        List<MessageAttachment> attachments = messageAttachmentRepository.findByMessageIdIn(List.of(savedMessage.getId()));
        List<MessageReaction> reactions = messageReactionRepository.findByMessageIdIn(List.of(savedMessage.getId()));
        MessageUserState actorState = messageUserStateRepository.findByMessageIdAndUserId(savedMessage.getId(), actorId)
                .orElse(null);
        MessageResponse response = mapToResponse(savedMessage, actorId, attachments, reactions, actorState);

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
        MessageReaction savedReaction = messageReactionRepository.save(reaction);

        broadcastReactionUpdate(message, userId, request.getReactionType());
        safeDispatchReactionNotification(message, userId, savedReaction);
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
        getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, userId);
        Instant now = Instant.now();
        messageUserStateRepository.markConversationAsSeen(conversationId, userId, now, now);

        messagingTemplate.convertAndSend("/topic/users/" + userId + "/conversations/status",
                RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED,
                        new ConversationStatusPayload(conversationId, "SEEN", null)));
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
        getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public TypingRealtimePayload buildTypingRealtimePayload(UUID conversationId, UUID userId, boolean isTyping) {
        String displayName = userProfileRepository.findById(userId)
                .filter(profile -> !profile.isDeleted())
                .map(this::resolveUserDisplayName)
                .orElse(userId.toString());

        return TypingRealtimePayload.builder()
                .conversationId(conversationId)
                .userId(userId)
                .senderId(userId)
                .isTyping(isTyping)
                .displayName(displayName)
                .build();
    }

    private String resolveUserDisplayName(UserProfile userProfile) {
        return resolveUserDisplayName(userProfile, null);
    }

    private String resolveUserDisplayName(UserProfile userProfile, UUID fallbackUserId) {
        if (userProfile == null) {
            return fallbackUserId != null ? fallbackUserId.toString() : null;
        }

        String displayName = userProfile.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }

        String fullName = resolveFullName(userProfile);
        if (fullName != null) {
            return fullName;
        }

        String username = userProfile.getUsername();
        if (username != null && !username.isBlank()) {
            return username;
        }

        UUID userId = userProfile.getUserId() != null ? userProfile.getUserId() : fallbackUserId;
        return userId != null ? userId.toString() : null;
    }

    private String resolveFullName(UserProfile userProfile) {
        String firstName = userProfile.getFirstName();
        String lastName = userProfile.getLastName();

        String fullName = String.join(" ",
                firstName != null ? firstName.trim() : "",
                lastName != null ? lastName.trim() : "").trim();
        return fullName.isEmpty() ? null : fullName;
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

        Map<Long, Message> replyMessagesByReplyToId = loadReplyMessagesByReplyToId(messages);
        Set<UUID> relatedUserIds = new HashSet<>();
        messages.stream()
                .map(Message::getSenderId)
                .filter(java.util.Objects::nonNull)
                .forEach(relatedUserIds::add);
        messages.stream()
                .map(Message::getReplyToSenderId)
                .filter(java.util.Objects::nonNull)
                .forEach(relatedUserIds::add);
        replyMessagesByReplyToId.values().stream()
                .map(Message::getSenderId)
                .filter(java.util.Objects::nonNull)
                .forEach(relatedUserIds::add);
        Map<UUID, UserProfile> profilesByUserId = loadUserProfilesByUserId(relatedUserIds);

        return messages.stream()
                .map(message -> mapToResponse(
                        message,
                        currentUserId,
                        attachmentsByMessageId.getOrDefault(message.getId(), List.of()),
                        reactionsByMessageId.getOrDefault(message.getId(), List.of()),
                        statesByMessageId.get(message.getId()),
                        profilesByUserId,
                        replyMessagesByReplyToId
                ))
                .toList();
    }

    private MessageResponse mapToResponse(
            Message message,
            UUID currentUserId,
            List<MessageAttachment> attachments,
            List<MessageReaction> reactions) {
        return mapToResponse(message, currentUserId, attachments, reactions, null, null, null);
    }

    private MessageResponse mapToResponse(
            Message message,
            UUID currentUserId,
            List<MessageAttachment> attachments,
            List<MessageReaction> reactions,
            MessageUserState state) {
        return mapToResponse(message, currentUserId, attachments, reactions, state, null, null);
    }

    private MessageResponse mapToResponse(
            Message message,
            UUID currentUserId,
            List<MessageAttachment> attachments,
            List<MessageReaction> reactions,
            MessageUserState state,
            Map<UUID, UserProfile> profilesByUserId,
            Map<Long, Message> replyMessagesByReplyToId) {

        List<MessageAttachmentResponse> attachmentResponses = attachments.stream()
                .map(this::mapAttachment)
                .toList();
        UserProfile senderProfile = resolveUserProfile(message.getSenderId(), profilesByUserId);
        String senderDisplayName = resolveUserDisplayName(senderProfile, message.getSenderId());
        String senderAvatarUrl = resolveUserAvatarUrl(senderProfile);

        log.debug("[BE MESSAGE SENDER MAP] messageId={} senderId={} displayName={} avatarUrl={}",
                message.getId(),
                message.getSenderId(),
                senderDisplayName,
                senderAvatarUrl);

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderDisplayName(senderDisplayName)
                .senderAvatarUrl(senderAvatarUrl)
                .content(message.getContent())
                .originalLinkUrl(resolveOriginalLinkUrl(message.getContent(), message.getOriginalLinkUrl()))
                .type(message.getMessageType())
                .replyTo(buildReplyInfo(message, profilesByUserId, replyMessagesByReplyToId))
                .attachments(attachmentResponses)
                .reactions(summarizeReactions(reactions))
                .myReaction(findMyReaction(reactions, currentUserId))
                .seen(resolveSeen(state))
                .createdAt(message.getCreatedAt())
                .editedAt(message.getEditedAt())
                .pinnedAt(message.getPinnedAt())
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
                .durationMs(attachment.getDurationMs())
                .waveform(deserializeWaveform(attachment.getWaveform()))
                .audioFormat(attachment.getAudioFormat())
                .build();
    }

    private ReplyInfo buildReplyInfo(
            Message message,
            Map<UUID, UserProfile> profilesByUserId,
            Map<Long, Message> replyMessagesByReplyToId) {
        if (message.getReplyToMessageId() == null) {
            return null;
        }

        Message repliedMessage = resolveReplyMessage(message.getReplyToMessageId(), replyMessagesByReplyToId);
        UUID replySenderId = repliedMessage != null && repliedMessage.getSenderId() != null
                ? repliedMessage.getSenderId()
                : message.getReplyToSenderId();
        UserProfile replySenderProfile = resolveUserProfile(replySenderId, profilesByUserId);
        String replySenderDisplayName = resolveUserDisplayName(replySenderProfile, replySenderId);
        String replySenderAvatarUrl = resolveUserAvatarUrl(replySenderProfile);

        log.debug("[BE REPLY SENDER MAP] messageId={} replyMessageId={} senderId={} displayName={} avatarUrl={}",
                message.getId(),
                message.getReplyToMessageId(),
                replySenderId,
                replySenderDisplayName,
                replySenderAvatarUrl);

        return ReplyInfo.builder()
                .messageId(message.getReplyToMessageId())
                .senderId(replySenderId)
                .senderDisplayName(replySenderDisplayName)
                .senderAvatarUrl(replySenderAvatarUrl)
                .contentPreview(message.getReplyToContentPreview())
                .type(repliedMessage != null && repliedMessage.getMessageType() != null
                        ? repliedMessage.getMessageType()
                        : message.getReplyToType())
                .build();
    }

    private Message resolveReplyMessage(Long replyToMessageId, Map<Long, Message> replyMessagesByReplyToId) {
        if (replyToMessageId == null) {
            return null;
        }

        if (replyMessagesByReplyToId != null && replyMessagesByReplyToId.containsKey(replyToMessageId)) {
            return replyMessagesByReplyToId.get(replyToMessageId);
        }

        return messageRepository.findById(replyToMessageId).orElse(null);
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
        boolean hasOriginalLinkUrl = request.getOriginalLinkUrl() != null && !request.getOriginalLinkUrl().isBlank();

        if (!hasContent && !hasAttachments && !hasOriginalLinkUrl) {
            throw new BusinessException("Message must contain text, original link, or attachments");
        }

        validateAudioPayload(request);
    }

    private String validateEditedContent(EditMessageRequest request, String existingContent, String existingOriginalLinkUrl) {
        if (request == null) {
            throw new BusinessException("Message content is required");
        }

        String trimmedContent = request.getContent() == null ? null : request.getContent().trim();
        String trimmedOriginalLinkUrl = normalizeLinkUrl(request.getOriginalLinkUrl());

        if ((trimmedContent == null || trimmedContent.isEmpty())
                && (trimmedOriginalLinkUrl == null || trimmedOriginalLinkUrl.isBlank())) {
            throw new BusinessException("Message content or original link is required");
        }

        String normalizedExistingContent = existingContent == null ? null : existingContent.trim();
        if (Objects.equals(trimmedContent, normalizedExistingContent)
                && Objects.equals(trimmedOriginalLinkUrl, normalizeLinkUrl(existingOriginalLinkUrl))) {
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
        message.setReplyToContentPreview(buildContentPreview(repliedMessage.getContent(), repliedMessage.getOriginalLinkUrl()));
    }

    private String buildContentPreview(String content, String originalLinkUrl) {
        String source = content;
        if (source == null || source.isBlank()) {
            source = originalLinkUrl;
        }
        if (source == null || source.isBlank()) {
            return "";
        }

        String trimmed = source.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    private void saveAttachments(Long messageId, List<MessageAttachmentPayload> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        attachments.forEach(payload -> {
            boolean isAudioAttachment = payload.getType() == MessageType.AUDIO;
            validateAttachmentAudioMetadata(payload, isAudioAttachment);

            MessageAttachment attachment = new MessageAttachment();
            attachment.setMessageId(messageId);
            attachment.setFileUrl(payload.getUrl());
            attachment.setStorageKey(payload.getStorageKey());
            attachment.setOriginalFileName(payload.getFileName());
            attachment.setFileType(payload.getContentType());
            attachment.setAttachmentType(payload.getType());
            attachment.setFileSize(payload.getFileSize());
            attachment.setDurationMs(payload.getDurationMs());
            attachment.setWaveform(serializeWaveform(payload.getWaveform()));
            attachment.setAudioFormat(normalizeAudioFormat(payload.getAudioFormat()));
            messageAttachmentRepository.save(attachment);
        });
    }

    private void validateAudioPayload(SendMessageRequest request) {
        List<MessageAttachmentPayload> attachments = request.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            if (request.getMessageType() == MessageType.AUDIO) {
                throw new BusinessException("Audio message must include at least one audio attachment");
            }
            return;
        }

        boolean hasAudioAttachment = false;

        for (MessageAttachmentPayload payload : attachments) {
            boolean isAudioAttachment = payload.getType() == MessageType.AUDIO;
            if (isAudioAttachment) {
                hasAudioAttachment = true;
            }
            validateAttachmentAudioMetadata(payload, isAudioAttachment);
        }

        if (request.getMessageType() == MessageType.AUDIO && !hasAudioAttachment) {
            throw new BusinessException("Audio message must include at least one audio attachment");
        }
    }

    private void validateAttachmentAudioMetadata(MessageAttachmentPayload payload, boolean isAudioAttachment) {
        boolean hasAudioMetadata = payload.getDurationMs() != null
                || payload.getWaveform() != null
                || normalizeAudioFormat(payload.getAudioFormat()) != null;

        if (hasAudioMetadata && !isAudioAttachment) {
            throw new BusinessException("Audio metadata can only be attached to audio files");
        }

        if (!isAudioAttachment) {
            return;
        }

        Long durationMs = payload.getDurationMs();
        if (durationMs != null && (durationMs <= 0 || durationMs > MAX_AUDIO_DURATION_MS)) {
            throw new BusinessException("Audio duration exceeds allowed maximum of 5 minutes");
        }

        List<Double> waveform = payload.getWaveform();
        if (waveform != null) {
            if (waveform.size() < MIN_WAVEFORM_SAMPLES || waveform.size() > MAX_WAVEFORM_SAMPLES) {
                throw new BusinessException("Waveform must contain between 16 and 128 samples");
            }

            boolean hasInvalidSample = waveform.stream()
                    .anyMatch(sample -> sample == null
                            || sample.isNaN()
                            || sample.isInfinite()
                            || sample < 0
                            || sample > 1);

            if (hasInvalidSample) {
                throw new BusinessException("Waveform samples must be normalized between 0 and 1");
            }
        }

        String normalizedAudioFormat = normalizeAudioFormat(payload.getAudioFormat());
        if (normalizedAudioFormat != null && normalizedAudioFormat.length() > MAX_AUDIO_FORMAT_LENGTH) {
            throw new BusinessException("Audio format value is too long");
        }
    }

    private String normalizeAudioFormat(String audioFormat) {
        String normalizedValue = normalizeNullableText(audioFormat);
        return normalizedValue == null ? null : normalizedValue.toLowerCase(Locale.ROOT);
    }

    private String serializeWaveform(List<Double> waveform) {
        if (waveform == null) {
            return null;
        }

        if (waveform.isEmpty()) {
            return "[]";
        }

        return waveform.stream()
                .map(sample -> Double.toString(sample))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private List<Double> deserializeWaveform(String waveformJson) {
        String normalized = normalizeNullableText(waveformJson);
        if (normalized == null) {
            return null;
        }

        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            return null;
        }

        String body = normalized.substring(1, normalized.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }

        String[] tokens = body.split(",");
        List<Double> values = new ArrayList<>(tokens.length);

        for (String token : tokens) {
            try {
                values.add(Double.parseDouble(token.trim()));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        return values;
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
        getConversationMemberOrThrow(conversationId, userId);
    }

    private ConversationMember getConversationMemberOrThrow(UUID conversationId, UUID userId) {
        return conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("User does not belong to this conversation"));
    }

    private void ensureCanPinMessages(Conversation conversation, ConversationMember actorMember) {
        if (conversation.getType() != ConversationType.GROUP) {
            return;
        }
        if (actorMember.getRole() != MemberRole.OWNER && actorMember.getRole() != MemberRole.ADMIN) {
            throw new ForbiddenException("Only owners or admins can pin group messages");
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

    private Message getVisibleMessageForUserOrThrow(Long messageId, UUID userId) {
        Message message = getVisibleMessageOrThrow(messageId);
        MessageUserState state = messageUserStateRepository.findByMessageIdAndUserId(messageId, userId).orElse(null);
        if (state != null && (state.getHiddenAt() != null || state.getDeletedForMeAt() != null)) {
            throw new NotFoundException("Message not found");
        }
        return message;
    }

    private void broadcastConversationUpdates(UUID conversationId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        conversationMemberRepository.findByConversationId(conversationId).forEach(member -> {
            ConversationUserSetting setting = findConversationUserSetting(conversationId, member.getUserId());
            if (!shouldDeliverConversationRefresh(setting)) {
                return;
            }

            ConversationResponse response = buildConversationResponse(conversation, member.getUserId(), setting);
            messagingTemplate.convertAndSend("/topic/users/" + member.getUserId() + "/conversations",
                    RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED, response));
        });
    }

    private void broadcastConversationUpdatesForNewMessage(Conversation conversation, Message message) {
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
        Set<UUID> mentionedUserIds = resolveMentionedUserIds(conversation, members, message.getContent());

        members.forEach(member -> {
            ConversationUserSetting setting = findConversationUserSetting(conversation.getId(), member.getUserId());
            if (!shouldDeliverConversationRefreshForNewMessage(conversation, member.getUserId(), setting, mentionedUserIds)) {
                return;
            }

            ConversationResponse response = buildConversationResponse(conversation, member.getUserId(), setting);
            log.debug("[BE UNREAD REFRESH] conversationId={} userId={} notificationLevel={} mentioned={} unreadCount={} deliver=true",
                    conversation.getId(),
                    member.getUserId(),
                    resolveNotificationLevel(setting),
                    mentionedUserIds.contains(member.getUserId()),
                    response.getUnreadCount());
            messagingTemplate.convertAndSend("/topic/users/" + member.getUserId() + "/conversations",
                    RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED, response));
        });
    }

    private ConversationResponse buildConversationResponse(Conversation conversation, UUID userId, ConversationUserSetting setting) {
        List<Message> lastMessages = messageRepository.findVisibleMessages(
                conversation.getId(),
                userId,
                PageRequest.of(0, 1)
        );
        Message lastMessage = lastMessages.isEmpty() ? null : lastMessages.get(0);
        String displayName = resolveDisplayName(conversation, setting);
        List<ConversationMemberResponse> groupMembers = buildGroupMembers(conversation);
        GroupConversationLabel groupLabel = conversation.getType() == ConversationType.GROUP && setting != null
                ? setting.getGroupLabel()
                : null;

        ConversationResponse response = ConversationResponse.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .avatarUrl(conversation.getAvatarUrl())
                .type(String.valueOf(conversation.getType()))
                .lastMessage(lastMessage != null ? resolveConversationPreviewText(lastMessage) : "")
                .lastMessageTime(lastMessage != null ? lastMessage.getCreatedAt() : conversation.getCreatedAt())
                .unreadCount(messageUserStateRepository.countUnreadMessages(conversation.getId(), userId))
                .muted(setting != null && setting.getMutedAt() != null)
                .archived(setting != null && setting.getArchivedAt() != null)
                .pinned(setting != null && setting.getPinnedAt() != null)
                .notificationLevel(resolveNotificationLevel(setting))
                .customName(setting != null ? setting.getCustomName() : null)
                .groupLabel(groupLabel != null ? groupLabel.name() : null)
                .groupLabelDisplayName(groupLabel != null ? groupLabel.getDisplayName() : null)
                .groupLabelColor(groupLabel != null ? groupLabel.getColor() : null)
                .displayName(displayName)
                .members(groupMembers)
                .build();

        if (conversation.getType() == ConversationType.GROUP) {
            log.debug("[BE GROUP RESPONSE MEMBERS] conversationId={} userId={} source=message-service memberCount={}",
                    conversation.getId(),
                    userId,
                    groupMembers != null ? groupMembers.size() : 0);
        }

        return response;
    }

    private String resolveConversationPreviewText(Message message) {
        if (message == null) {
            return "";
        }

        if (message.getMessageType() == MessageType.SYSTEM) {
            return resolveGroupSystemPreviewText(message.getContent());
        }

        return message.getContent();
    }

    private String resolveGroupSystemPreviewText(String content) {
        String normalized = content == null ? "" : content.trim();
        if (!normalized.startsWith(GROUP_SYSTEM_PREFIX)) {
            return "Hoạt động nhóm";
        }

        String kind = extractGroupSystemJsonField(normalized, "kind");
        String actorName = defaultIfBlank(extractGroupSystemJsonField(normalized, "actorName"), "Ai đó");
        String targetName = defaultIfBlank(extractGroupSystemJsonField(normalized, "targetName"), "một thành viên");
        String nickname = extractGroupSystemJsonField(normalized, "nickname");
        String groupName = defaultIfBlank(
                extractGroupSystemJsonField(normalized, "name"),
                defaultIfBlank(extractGroupSystemJsonField(normalized, "conversationName"), "nhóm"));

        return switch (kind) {
            case "group_member_added" -> actorName + " đã thêm " + targetName + " vào nhóm";
            case "group_member_removed" -> actorName + " đã xóa " + targetName + " khỏi nhóm";
            case "group_left" -> actorName + " đã rời nhóm";
            case "group_admin_promoted" -> actorName + " đã cấp phó nhóm cho " + targetName;
            case "group_admin_demoted" -> actorName + " đã thu hồi phó nhóm của " + targetName;
            case "group_owner_transferred" -> actorName + " đã chuyển quyền trưởng nhóm cho " + targetName;
            case "group_renamed" -> actorName + " đã đổi tên nhóm thành \"" + groupName + "\"";
            case "group_avatar_changed" -> actorName + " đã cập nhật ảnh nhóm";
            case "group_background_changed" -> actorName + " đã đổi nền chat";
            case "group_nickname_changed" -> nickname == null || nickname.isBlank()
                    ? actorName + " đã xóa biệt danh của " + targetName
                    : actorName + " đã đổi biệt danh của " + targetName + " thành \"" + nickname + "\"";
            case "group_disbanded" -> actorName + " đã giải tán nhóm " + groupName;
            default -> "Hoạt động nhóm";
        };
    }

    private String extractGroupSystemJsonField(String content, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(SIMPLE_JSON_STRING_FIELD_REGEX, fieldName)).matcher(content);
        if (!matcher.find()) {
            return "";
        }

        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private ConversationUserSetting findConversationUserSetting(UUID conversationId, UUID userId) {
        return conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);
    }

    private ConversationNotificationLevel resolveNotificationLevel(ConversationUserSetting setting) {
        return setting != null && setting.getNotificationLevel() != null
                ? setting.getNotificationLevel()
                : ConversationNotificationLevel.ALL;
    }

    private boolean shouldDeliverConversationRefresh(ConversationUserSetting setting) {
        ConversationNotificationLevel notificationLevel = resolveNotificationLevel(setting);
        log.debug("[BE UNREAD VS NOTIFY POLICY] scope=conversation-refresh notificationLevel={} deliver=true reason=conversation-state-sync",
                notificationLevel);
        return true;
    }

    private boolean shouldDeliverConversationRefreshForNewMessage(
            Conversation conversation,
            UUID userId,
            ConversationUserSetting setting,
            Set<UUID> mentionedUserIds) {

        ConversationNotificationLevel notificationLevel = resolveNotificationLevel(setting);
        boolean mentioned = mentionedUserIds.contains(userId);
        log.debug("[BE UNREAD VS NOTIFY POLICY] scope=new-message-refresh conversationId={} userId={} notificationLevel={} mentioned={} deliver=true reason=unread-sync",
                conversation.getId(),
                userId,
                notificationLevel,
                mentioned);
        return true;
    }

    private void safeDispatchMessageNotifications(Conversation conversation, Message message) {
        if (notificationDispatcher == null || notificationRecipientResolver == null || notificationContentBuilder == null) {
            return;
        }
        try {
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
            Set<UUID> mentionedUserIds = resolveMentionedUserIds(conversation, members, message.getContent());
            List<ResolvedNotificationRecipient> recipients = notificationRecipientResolver
                    .resolveMessageRecipients(message, conversation, mentionedUserIds);
            if (recipients.isEmpty()) {
                return;
            }

            String actorName = userProfileRepository.findById(message.getSenderId())
                    .map(profile -> resolveUserDisplayName(profile, message.getSenderId()))
                    .orElse("Ai đó");
            String messagePreview = notificationContentBuilder.buildMessagePreview(
                    message.getContent(),
                    message.getOriginalLinkUrl(),
                    message.getMessageType());
            String conversationName = resolveDisplayName(conversation, null);

            for (ResolvedNotificationRecipient recipient : recipients) {
                notificationDispatcher.dispatch(NotificationDispatchRequest.builder()
                        .type(recipient.getType())
                        .targetType(NotificationTargetType.CONVERSATION)
                        .targetId(conversation.getId())
                        .actorId(message.getSenderId())
                        .explicitRecipientIds(List.of(recipient.getUserId()))
                        .conversationId(conversation.getId())
                        .messageId(message.getId())
                        .metadata(Map.of(
                                "actorName", actorName,
                                "conversationName", conversationName != null ? conversationName : "Cuộc trò chuyện",
                                "messagePreview", messagePreview
                        ))
                        .dedupKeyPrefix(recipient.getType().name() + ":" + message.getId())
                        .directMention(recipient.isDirectMention())
                        .replyToRecipientMessage(recipient.isReplyToRecipientMessage())
                        .recipientDirectlyAffected(recipient.isRecipientDirectlyAffected())
                        .build());
            }
        } catch (Exception ex) {
            log.warn("[Notification] Failed to dispatch message notifications messageId={} conversationId={}: {}",
                    message.getId(),
                    conversation.getId(),
                    ex.getMessage());
        }
    }

    private void safeDispatchReactionNotification(Message message, UUID actorId, MessageReaction reaction) {
        if (notificationDispatcher == null || notificationRecipientResolver == null) {
            return;
        }
        try {
            List<UUID> recipients = notificationRecipientResolver.resolveReactionRecipients(message, actorId);
            if (recipients.isEmpty()) {
                return;
            }
            Conversation conversation = getConversationOrThrow(message.getConversationId());
            String actorName = userProfileRepository.findById(actorId)
                    .map(profile -> resolveUserDisplayName(profile, actorId))
                    .orElse("Ai đó");
            String conversationName = resolveDisplayName(conversation, null);
            notificationDispatcher.dispatch(NotificationDispatchRequest.builder()
                    .type(NotificationType.REACTION_TO_MY_MESSAGE)
                    .targetType(NotificationTargetType.MESSAGE)
                    .actorId(actorId)
                    .explicitRecipientIds(recipients)
                    .conversationId(message.getConversationId())
                    .messageId(message.getId())
                    .metadata(Map.of(
                            "actorName", actorName,
                            "conversationName", conversationName != null ? conversationName : "Cuộc trò chuyện",
                            "reactionType", reaction.getReactionType() != null ? reaction.getReactionType().name() : ""
                    ))
                    .dedupKeyPrefix(NotificationType.REACTION_TO_MY_MESSAGE.name() + ":" + reaction.getId())
                    .recipientDirectlyAffected(true)
                    .build());
        } catch (Exception ex) {
            log.warn("[Notification] Failed to dispatch reaction notification messageId={} actorId={}: {}",
                    message.getId(),
                    actorId,
                    ex.getMessage());
        }
    }

    private Set<UUID> resolveMentionedUserIds(Conversation conversation, List<ConversationMember> members, String content) {
        if (conversation.getType() != ConversationType.GROUP) {
            return Set.of();
        }

        Set<String> mentionedUsernames = extractMentionedUsernames(content);
        if (mentionedUsernames.isEmpty()) {
            return Set.of();
        }

        Set<UUID> memberIds = members.stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        return userProfileRepository.findAllById(memberIds).stream()
                .filter(profile -> !profile.isDeleted())
                .filter(profile -> profile.getUsername() != null && !profile.getUsername().isBlank())
                .filter(profile -> mentionedUsernames.contains(profile.getUsername().toLowerCase(Locale.ROOT)))
                .map(UserProfile::getUserId)
                .collect(Collectors.toSet());
    }

    private Set<String> extractMentionedUsernames(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }

        Matcher matcher = MENTION_PATTERN.matcher(content);
        Set<String> usernames = new HashSet<>();
        while (matcher.find()) {
            usernames.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return usernames;
    }

    private String resolveDisplayName(Conversation conversation, ConversationUserSetting setting) {
        if (setting != null && setting.getCustomName() != null) {
            return setting.getCustomName();
        }
        return conversation.getName();
    }

    private List<ConversationMemberResponse> buildGroupMembers(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            return null;
        }

        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
        Map<UUID, UserProfile> profilesByUserId = loadUserProfilesByUserId(members.stream()
                .map(ConversationMember::getUserId)
                .toList());
        List<ConversationMemberResponse> memberResponses = members.stream()
                .map(member -> mapConversationMember(member, profilesByUserId.get(member.getUserId())))
                .toList();

        log.debug("[BE GROUP RESPONSE MEMBERS] conversationId={} source=message-builder memberCount={}",
                conversation.getId(),
                memberResponses.size());

        return memberResponses;
    }

    private ConversationMemberResponse mapConversationMember(ConversationMember member, UserProfile userProfile) {
        return ConversationMemberResponse.builder()
                .userId(member.getUserId())
                .username(userProfile != null ? normalizeNullableText(userProfile.getUsername()) : null)
                .displayName(resolveUserDisplayName(userProfile, member.getUserId()))
                .avatarUrl(userProfile != null ? normalizeNullableText(userProfile.getAvatarUrl()) : null)
                .role(member.getRole())
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

    private List<Message> loadVisibleMessages(UUID conversationId, UUID userId, MessageCursor cursor, Pageable pageable) {
        if (!cursor.isPresent()) {
            return messageRepository.findVisibleMessages(conversationId, userId, pageable);
        }

        return messageRepository.findVisibleMessagesBeforeCursor(
                conversationId,
                userId,
                cursor.createdAt(),
                cursor.messageId(),
                pageable
        );
    }

    private Message resolveLatestVisibleMessage(UUID conversationId, UUID userId) {
        List<Message> latestMessages = messageRepository.findVisibleMessages(conversationId, userId, PageRequest.of(0, 1));
        return latestMessages.isEmpty() ? null : latestMessages.get(0);
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
        private boolean isPresent() {
            return createdAt != null && messageId != null;
        }
    }

    private Map<UUID, UserProfile> loadUserProfilesByUserId(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UserProfile> profilesByUserId = new LinkedHashMap<>();
        userProfileRepository.findAllById(userIds).stream()
                .filter(userProfile -> !userProfile.isDeleted())
                .forEach(userProfile -> profilesByUserId.put(userProfile.getUserId(), userProfile));
        return profilesByUserId;
    }

    private UserProfile resolveUserProfile(UUID userId, Map<UUID, UserProfile> profilesByUserId) {
        if (userId == null) {
            return null;
        }

        if (profilesByUserId != null && profilesByUserId.containsKey(userId)) {
            return profilesByUserId.get(userId);
        }

        return userProfileRepository.findById(userId)
                .filter(userProfile -> !userProfile.isDeleted())
                .orElse(null);
    }

    private String resolveUserAvatarUrl(UserProfile userProfile) {
        return userProfile != null ? normalizeNullableText(userProfile.getAvatarUrl()) : null;
    }

    private Map<Long, Message> loadReplyMessagesByReplyToId(List<Message> messages) {
        List<Long> replyToMessageIds = messages.stream()
                .map(Message::getReplyToMessageId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (replyToMessageIds.isEmpty()) {
            return Map.of();
        }

        return messageRepository.findAllById(replyToMessageIds).stream()
                .collect(Collectors.toMap(Message::getId, replyMessage -> replyMessage));
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private String normalizeLinkUrl(String value) {
        String normalizedValue = normalizeNullableText(value);
        if (normalizedValue == null) {
            return null;
        }
        if (normalizedValue.length() > 2000) {
            throw new BusinessException("Original link URL must be less than 2000 characters");
        }
        return normalizedValue;
    }

    private int normalizeContextRange(int range) {
        if (range <= 0) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.min(range, MAX_MESSAGE_PAGE_SIZE);
    }

    private String resolveOriginalLinkUrl(String content, String originalLinkUrl) {
        String normalizedOriginalLinkUrl = normalizeLinkUrl(originalLinkUrl);
        if (normalizedOriginalLinkUrl != null) {
            return normalizedOriginalLinkUrl;
        }

        String normalizedContent = normalizeNullableText(content);
        if (normalizedContent != null && ABSOLUTE_URL_PATTERN.matcher(normalizedContent).matches()) {
            return normalizeLinkUrl(normalizedContent);
        }

        return null;
    }
}
