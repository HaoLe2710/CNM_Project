package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.EditMessageRequest;
import fit.iuh.cnm_project_be.message.dto.MessageAttachmentPayload;
import fit.iuh.cnm_project_be.message.dto.MessageDeliveryReceiptPayload;
import fit.iuh.cnm_project_be.message.dto.MessageReadReceiptPayload;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.entity.ConversationMemberReadState;
import fit.iuh.cnm_project_be.message.entity.MessageAttachment;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.message.repository.ConversationMemberReadStateRepository;
import fit.iuh.cnm_project_be.message.repository.MessageReactionRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserBlockRepository;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;
    @Mock
    private MessageReactionRepository messageReactionRepository;
    @Mock
    private ConversationMemberReadStateRepository conversationMemberReadStateRepository;
    @Mock
    private MessageStatusRepository messageStatusRepository;
    @Mock
    private MessageUserStateRepository messageUserStateRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private ConversationUserSettingRepository conversationUserSettingRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private S3MediaStorageService s3MediaStorageService;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendMessageCreatesUserStatesWithSenderSeenAndRecipientUnseen() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(senderId);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello");

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(100L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(100L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(100L, senderId)).thenReturn(Optional.of(senderState));

        messageService.sendMessage(senderId, request);

        ArgumentCaptor<MessageUserState> stateCaptor = ArgumentCaptor.forClass(MessageUserState.class);
        verify(messageUserStateRepository, org.mockito.Mockito.times(2)).save(stateCaptor.capture());
        List<MessageUserState> savedStates = stateCaptor.getAllValues();

        MessageUserState savedSenderState = savedStates.stream()
                .filter(state -> state.getUserId().equals(senderId))
                .findFirst()
                .orElseThrow();
        MessageUserState savedRecipientState = savedStates.stream()
                .filter(state -> state.getUserId().equals(recipientId))
                .findFirst()
                .orElseThrow();

        assertThat(savedSenderState.getSeenAt()).isNotNull();
        assertThat(savedRecipientState.getSeenAt()).isNull();
    }

    @Test
    void sendMessageIncludesSenderIdentityInResponseAndMessageCreatedPayload() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(senderId);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello");

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(109L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(109L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(109L, senderId)).thenReturn(Optional.of(senderState));
        when(userProfileRepository.findById(senderId))
                .thenReturn(Optional.of(activeUser(senderId, "sender.user", "Sender Name", "https://cdn.example.com/sender.png")));
        when(messageRepository.findVisibleMessages(eq(conversationId), any(UUID.class), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(eq(conversationId), any(UUID.class))).thenReturn(0L);

        MessageResponse response = messageService.sendMessage(senderId, request);

        assertThat(response.getSenderDisplayName()).isEqualTo("Sender Name");
        assertThat(response.getSenderAvatarUrl()).isEqualTo("https://cdn.example.com/sender.png");

        ArgumentCaptor<RealtimeEvent<?>> eventCaptor = ArgumentCaptor.forClass((Class) RealtimeEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/" + conversationId), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getPayload()).isInstanceOf(MessageResponse.class);
        MessageResponse realtimePayload = (MessageResponse) eventCaptor.getValue().getPayload();
        assertThat(realtimePayload.getSenderDisplayName()).isEqualTo("Sender Name");
        assertThat(realtimePayload.getSenderAvatarUrl()).isEqualTo("https://cdn.example.com/sender.png");
    }

    @Test
    void sendMessageWithImageAttachmentUsesAttachmentMetadataAndImageType() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(senderId);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setAttachments(List.of(attachmentPayload(
                "https://cdn.example.com/chat/img.png",
                "chat/u1/img.png",
                "img.png",
                "image/png",
                2048L,
                MessageType.IMAGE
        )));

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(106L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId)))
                .thenReturn(List.of(member(conversationId, senderId)))
                .thenReturn(List.of(member(conversationId, senderId)));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(106L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(106L, senderId)).thenReturn(Optional.of(senderState));
        when(messageAttachmentRepository.findByMessageIdIn(List.of(106L))).thenReturn(List.of(savedAttachment(
                106L,
                "https://cdn.example.com/chat/img.png",
                "chat/u1/img.png",
                "img.png",
                "image/png",
                2048L,
                MessageType.IMAGE
        )));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);

        MessageResponse response = messageService.sendMessage(senderId, request);

        ArgumentCaptor<MessageAttachment> attachmentCaptor = ArgumentCaptor.forClass(MessageAttachment.class);
        verify(messageAttachmentRepository).save(attachmentCaptor.capture());
        MessageAttachment savedAttachment = attachmentCaptor.getValue();

        assertThat(response.getType()).isEqualTo(MessageType.IMAGE);
        assertThat(response.getAttachments()).hasSize(1);
        assertThat(response.getAttachments().get(0).getType()).isEqualTo(MessageType.IMAGE);
        assertThat(savedAttachment.getAttachmentType()).isEqualTo(MessageType.IMAGE);
        assertThat(savedAttachment.getStorageKey()).isEqualTo("chat/u1/img.png");
        assertThat(savedAttachment.getOriginalFileName()).isEqualTo("img.png");
        assertThat(savedAttachment.getFileType()).isEqualTo("image/png");
    }

    @Test
    void sendMessageWithDocumentAttachmentUsesFileType() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(senderId);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setAttachments(List.of(attachmentPayload(
                "https://cdn.example.com/chat/spec.pdf",
                "chat/u1/spec.pdf",
                "spec.pdf",
                "application/pdf",
                8192L,
                MessageType.FILE
        )));

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(107L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId)))
                .thenReturn(List.of(member(conversationId, senderId)))
                .thenReturn(List.of(member(conversationId, senderId)));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(107L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(107L, senderId)).thenReturn(Optional.of(senderState));
        when(messageAttachmentRepository.findByMessageIdIn(List.of(107L))).thenReturn(List.of(savedAttachment(
                107L,
                "https://cdn.example.com/chat/spec.pdf",
                "chat/u1/spec.pdf",
                "spec.pdf",
                "application/pdf",
                8192L,
                MessageType.FILE
        )));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);

        MessageResponse response = messageService.sendMessage(senderId, request);

        ArgumentCaptor<MessageAttachment> attachmentCaptor = ArgumentCaptor.forClass(MessageAttachment.class);
        verify(messageAttachmentRepository).save(attachmentCaptor.capture());
        MessageAttachment savedAttachment = attachmentCaptor.getValue();

        assertThat(response.getType()).isEqualTo(MessageType.FILE);
        assertThat(response.getAttachments()).hasSize(1);
        assertThat(response.getAttachments().get(0).getType()).isEqualTo(MessageType.FILE);
        assertThat(savedAttachment.getAttachmentType()).isEqualTo(MessageType.FILE);
        assertThat(savedAttachment.getOriginalFileName()).isEqualTo("spec.pdf");
        assertThat(savedAttachment.getFileType()).isEqualTo("application/pdf");
    }

    @Test
    void markAsSeenUpdatesOnlyMessageUserStates() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);

        messageService.markAsSeen(conversationId, userId);

        verify(messageUserStateRepository).markConversationAsSeen(eq(conversationId), eq(userId), any(Instant.class), any(Instant.class));
        verifyNoInteractions(messageStatusRepository);
    }

    @Test
    void markAsSeenWithLastReadMessageIdUpdatesCursorAndPublishesReadReceiptEvent() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        Message lastReadMessage = new Message();
        lastReadMessage.setId(88L);
        lastReadMessage.setConversationId(conversationId);
        lastReadMessage.setSenderId(UUID.randomUUID());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId)).thenReturn(List.of());
        when(messageRepository.findByIdAndDeletedAtIsNull(88L)).thenReturn(Optional.of(lastReadMessage));
        when(conversationMemberReadStateRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        messageService.markAsSeen(conversationId, userId, 88L);

        ArgumentCaptor<ConversationMemberReadState> readStateCaptor =
                ArgumentCaptor.forClass(ConversationMemberReadState.class);
        verify(conversationMemberReadStateRepository).save(readStateCaptor.capture());
        ConversationMemberReadState savedState = readStateCaptor.getValue();
        assertThat(savedState.getConversationId()).isEqualTo(conversationId);
        assertThat(savedState.getUserId()).isEqualTo(userId);
        assertThat(savedState.getLastReadMessageId()).isEqualTo(88L);
        assertThat(savedState.getLastReadAt()).isNotNull();

        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId),
                argThat((RealtimeEvent<?> event) -> {
                    if (event == null || event.getType() != RealtimeEventType.MESSAGE_READ_RECEIPT_UPDATED) {
                        return false;
                    }
                    Object payload = event.getPayload();
                    if (!(payload instanceof MessageReadReceiptPayload readReceiptPayload)) {
                        return false;
                    }
                    return conversationId.equals(readReceiptPayload.getConversationId())
                            && userId.equals(readReceiptPayload.getUserId())
                            && Long.valueOf(88L).equals(readReceiptPayload.getLastReadMessageId())
                            && readReceiptPayload.getReadAt() != null;
                })
        );
    }

    @Test
    void markAsSeenWithOlderCursorSkipsReadReceiptBroadcast() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        Message olderMessage = new Message();
        olderMessage.setId(22L);
        olderMessage.setConversationId(conversationId);
        olderMessage.setSenderId(UUID.randomUUID());

        ConversationMemberReadState existingState = new ConversationMemberReadState();
        existingState.setConversationId(conversationId);
        existingState.setUserId(userId);
        existingState.setLastReadMessageId(50L);
        existingState.setLastReadAt(Instant.now().minusSeconds(10));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepository.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(olderMessage));
        when(conversationMemberReadStateRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(existingState));

        messageService.markAsSeen(conversationId, userId, 22L);

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/conversations/" + conversationId), any(RealtimeEvent.class));
    }

    @Test
    void markAsDeliveredWithLastMessageIdUpdatesCursorAndPublishesDeliveryEvent() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        Message lastDeliveredMessage = new Message();
        lastDeliveredMessage.setId(135L);
        lastDeliveredMessage.setConversationId(conversationId);
        lastDeliveredMessage.setSenderId(UUID.randomUUID());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepository.findByIdAndDeletedAtIsNull(135L)).thenReturn(Optional.of(lastDeliveredMessage));
        when(conversationMemberReadStateRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        messageService.markAsDelivered(conversationId, userId, 135L);

        ArgumentCaptor<ConversationMemberReadState> readStateCaptor =
                ArgumentCaptor.forClass(ConversationMemberReadState.class);
        verify(conversationMemberReadStateRepository).save(readStateCaptor.capture());
        ConversationMemberReadState savedState = readStateCaptor.getValue();
        assertThat(savedState.getConversationId()).isEqualTo(conversationId);
        assertThat(savedState.getUserId()).isEqualTo(userId);
        assertThat(savedState.getLastDeliveredMessageId()).isEqualTo(135L);
        assertThat(savedState.getLastDeliveredAt()).isNotNull();

        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversations/" + conversationId),
                argThat((RealtimeEvent<?> event) -> {
                    if (event == null || event.getType() != RealtimeEventType.MESSAGE_DELIVERY_UPDATED) {
                        return false;
                    }
                    Object payload = event.getPayload();
                    if (!(payload instanceof MessageDeliveryReceiptPayload deliveryPayload)) {
                        return false;
                    }
                    return conversationId.equals(deliveryPayload.getConversationId())
                            && userId.equals(deliveryPayload.getUserId())
                            && Long.valueOf(135L).equals(deliveryPayload.getLastDeliveredMessageId())
                            && deliveryPayload.getDeliveredAt() != null;
                })
        );
    }

    @Test
    void markAsDeliveredWithOlderCursorSkipsDeliveryBroadcast() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        Message olderMessage = new Message();
        olderMessage.setId(42L);
        olderMessage.setConversationId(conversationId);
        olderMessage.setSenderId(UUID.randomUUID());

        ConversationMemberReadState existingState = new ConversationMemberReadState();
        existingState.setConversationId(conversationId);
        existingState.setUserId(userId);
        existingState.setLastDeliveredMessageId(100L);
        existingState.setLastDeliveredAt(Instant.now().minusSeconds(5));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(olderMessage));
        when(conversationMemberReadStateRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(existingState));

        messageService.markAsDelivered(conversationId, userId, 42L);

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/conversations/" + conversationId), any(RealtimeEvent.class));
    }

    @Test
    void markAsSeenWithSameCursorRapidlyIsThrottled() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        Message lastReadMessage = new Message();
        lastReadMessage.setId(77L);
        lastReadMessage.setConversationId(conversationId);
        lastReadMessage.setSenderId(UUID.randomUUID());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepository.findByIdAndDeletedAtIsNull(77L)).thenReturn(Optional.of(lastReadMessage));
        when(messageUserStateRepository.markConversationAsSeen(eq(conversationId), eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(conversationMemberReadStateRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        messageService.markAsSeen(conversationId, userId, 77L);
        messageService.markAsSeen(conversationId, userId, 77L);

        verify(messageRepository, times(2)).findByIdAndDeletedAtIsNull(77L);
        verify(messageUserStateRepository, times(1))
                .markConversationAsSeen(eq(conversationId), eq(userId), any(Instant.class), any(Instant.class));
        verify(conversationMemberReadStateRepository, times(1))
                .findByConversationIdAndUserId(conversationId, userId);
        verify(conversationMemberReadStateRepository, times(1)).save(any(ConversationMemberReadState.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/conversations/" + conversationId), any(RealtimeEvent.class));
    }

    @Test
    void markAsDeliveredWithSameCursorRapidlyIsThrottled() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        Message lastDeliveredMessage = new Message();
        lastDeliveredMessage.setId(91L);
        lastDeliveredMessage.setConversationId(conversationId);
        lastDeliveredMessage.setSenderId(UUID.randomUUID());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepository.findByIdAndDeletedAtIsNull(91L)).thenReturn(Optional.of(lastDeliveredMessage));
        when(conversationMemberReadStateRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        messageService.markAsDelivered(conversationId, userId, 91L);
        messageService.markAsDelivered(conversationId, userId, 91L);

        verify(messageRepository, times(2)).findByIdAndDeletedAtIsNull(91L);
        verify(conversationMemberReadStateRepository, times(1))
                .findByConversationIdAndUserId(conversationId, userId);
        verify(conversationMemberReadStateRepository, times(1)).save(any(ConversationMemberReadState.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/conversations/" + conversationId), any(RealtimeEvent.class));
    }

    @Test
    void updateStatusSeenUsesUserStateCompatibilityPathOnly() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(userId);

        Message message = new Message();
        message.setId(11L);
        message.setConversationId(conversationId);
        message.setSenderId(UUID.randomUUID());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId)).thenReturn(List.of());

        messageService.updateStatus(11L, userId, MessageDeliveryStatus.SEEN);

        verify(messageUserStateRepository).upsert(eq(11L), eq(userId), any());
        verifyNoInteractions(messageStatusRepository);
    }

    @Test
    void updateStatusDeliveredStillWritesTransportStatus() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Message message = new Message();
        message.setId(12L);
        message.setConversationId(conversationId);
        message.setSenderId(UUID.randomUUID());

        when(messageRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageStatusRepository.findByMessageIdAndUserId(12L, userId)).thenReturn(Optional.empty());

        messageService.updateStatus(12L, userId, MessageDeliveryStatus.DELIVERED);

        verify(messageStatusRepository).findByMessageIdAndUserId(12L, userId);
        verify(messageStatusRepository).save(any());
        verifyNoMoreInteractions(messageStatusRepository);
    }

    @Test
    void getMessagesUsesMessageUserStatesAsSeenSource() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        Message message = new Message();
        message.setId(10L);
        message.setConversationId(conversationId);
        message.setSenderId(UUID.randomUUID());
        message.setContent("visible");
        message.setMessageType(MessageType.TEXT);
        message.setCreatedAt(Instant.parse("2026-03-23T00:00:00Z"));

        MessageUserState state = new MessageUserState();
        state.setMessageId(10L);
        state.setUserId(userId);
        state.setSeenAt(Instant.parse("2026-03-23T00:01:00Z"));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(userId), any()))
                .thenReturn(List.of(message));
        when(messageAttachmentRepository.findByMessageIdIn(List.of(10L))).thenReturn(List.of());
        when(messageReactionRepository.findByMessageIdIn(List.of(10L))).thenReturn(List.of());
        when(messageUserStateRepository.findByMessageIdInAndUserId(List.of(10L), userId)).thenReturn(List.of(state));

        MessageResponse response = messageService.getMessages(conversationId, userId, null, 50).getItems().get(0);

        assertThat(response.getSeen()).isTrue();
        verify(messageRepository).findVisibleMessages(eq(conversationId), eq(userId), any());
        verify(messageRepository, never()).findVisibleMessagesBeforeCursor(any(), any(), any(), any(), any());
        verifyNoInteractions(messageStatusRepository);
    }

    @Test
    void getMessagesReturnsConversationMemberReadStates() {
        UUID conversationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID peerUserId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(currentUserId);
        conversation.setType(ConversationType.PRIVATE);

        Message message = new Message();
        message.setId(99L);
        message.setConversationId(conversationId);
        message.setSenderId(peerUserId);
        message.setContent("hello");
        message.setMessageType(MessageType.TEXT);
        message.setCreatedAt(Instant.parse("2026-05-31T00:00:00Z"));

        ConversationMemberReadState currentState = new ConversationMemberReadState();
        currentState.setConversationId(conversationId);
        currentState.setUserId(currentUserId);
        currentState.setLastReadMessageId(99L);
        currentState.setLastReadAt(Instant.parse("2026-05-31T00:01:00Z"));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, currentUserId)).thenReturn(true);
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(currentUserId), any()))
                .thenReturn(List.of(message));
        when(messageAttachmentRepository.findByMessageIdIn(List.of(99L))).thenReturn(List.of());
        when(messageReactionRepository.findByMessageIdIn(List.of(99L))).thenReturn(List.of());
        when(messageUserStateRepository.findByMessageIdInAndUserId(List.of(99L), currentUserId)).thenReturn(List.of());
        when(conversationMemberReadStateRepository.findActiveByConversationId(conversationId))
                .thenReturn(List.of(currentState));

        var page = messageService.getMessages(conversationId, currentUserId, null, 50);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getMemberReadStates()).hasSize(1);
        assertThat(page.getMemberReadStates())
                .anySatisfy(readState -> {
                    if (currentUserId.equals(readState.getUserId())) {
                        assertThat(readState.getLastReadMessageId()).isEqualTo(99L);
                        assertThat(readState.getLastReadAt()).isEqualTo(Instant.parse("2026-05-31T00:01:00Z"));
                    }
                });
    }

    @Test
    void getMessagesUsesCursorQueryWhenCursorPresent() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant cursorCreatedAt = Instant.parse("2026-03-23T00:00:00Z");
        String cursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((cursorCreatedAt.toEpochMilli() + ":10").getBytes(StandardCharsets.UTF_8));

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        Message message = new Message();
        message.setId(9L);
        message.setConversationId(conversationId);
        message.setSenderId(UUID.randomUUID());
        message.setContent("older");
        message.setMessageType(MessageType.TEXT);
        message.setCreatedAt(Instant.parse("2026-03-22T23:59:00Z"));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(messageRepository.findVisibleMessagesBeforeCursor(eq(conversationId), eq(userId), eq(cursorCreatedAt), eq(10L), any()))
                .thenReturn(List.of(message));
        when(messageAttachmentRepository.findByMessageIdIn(List.of(9L))).thenReturn(List.of());
        when(messageReactionRepository.findByMessageIdIn(List.of(9L))).thenReturn(List.of());
        when(messageUserStateRepository.findByMessageIdInAndUserId(List.of(9L), userId)).thenReturn(List.of());

        MessageResponse response = messageService.getMessages(conversationId, userId, cursor, 50).getItems().get(0);

        assertThat(response.getId()).isEqualTo(9L);
        verify(messageRepository).findVisibleMessagesBeforeCursor(eq(conversationId), eq(userId), eq(cursorCreatedAt), eq(10L), any());
        verify(messageRepository, never()).findVisibleMessages(eq(conversationId), eq(userId), any());
    }

    @Test
    void getMessagesIncludesSenderAndReplySenderIdentity() {
        UUID conversationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID replySenderId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(currentUserId);
        conversation.setType(ConversationType.GROUP);

        Message message = new Message();
        message.setId(31L);
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setContent("replying");
        message.setMessageType(MessageType.TEXT);
        message.setCreatedAt(Instant.parse("2026-03-23T00:00:00Z"));
        message.setReplyToMessageId(11L);
        message.setReplyToSenderId(replySenderId);
        message.setReplyToType(MessageType.IMAGE);
        message.setReplyToContentPreview("preview");

        Message repliedMessage = new Message();
        repliedMessage.setId(11L);
        repliedMessage.setConversationId(conversationId);
        repliedMessage.setSenderId(replySenderId);
        repliedMessage.setMessageType(MessageType.IMAGE);
        repliedMessage.setContent("original");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, currentUserId)).thenReturn(true);
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(currentUserId), any()))
                .thenReturn(List.of(message));
        when(messageAttachmentRepository.findByMessageIdIn(List.of(31L))).thenReturn(List.of());
        when(messageReactionRepository.findByMessageIdIn(List.of(31L))).thenReturn(List.of());
        when(messageUserStateRepository.findByMessageIdInAndUserId(List.of(31L), currentUserId)).thenReturn(List.of());
        when(messageRepository.findAllById(List.of(11L))).thenReturn(List.of(repliedMessage));
        when(userProfileRepository.findAllById(any(Iterable.class)))
                .thenReturn(List.of(
                        activeUser(senderId, "sender.user", "Sender Name", "https://cdn.example.com/sender.png"),
                        activeUser(replySenderId, "reply.user", "Reply Name", "https://cdn.example.com/reply.png")
                ));

        MessageResponse response = messageService.getMessages(conversationId, currentUserId, null, 50).getItems().get(0);

        assertThat(response.getSenderDisplayName()).isEqualTo("Sender Name");
        assertThat(response.getSenderAvatarUrl()).isEqualTo("https://cdn.example.com/sender.png");
        assertThat(response.getReplyTo()).isNotNull();
        assertThat(response.getReplyTo().getSenderDisplayName()).isEqualTo("Reply Name");
        assertThat(response.getReplyTo().getSenderAvatarUrl()).isEqualTo("https://cdn.example.com/reply.png");
    }

    @Test
    void editMessageUpdatesContentAndEditedAt() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Message message = new Message();
        message.setId(20L);
        message.setConversationId(conversationId);
        message.setSenderId(actorId);
        message.setContent("before");
        message.setMessageType(MessageType.TEXT);

        EditMessageRequest request = new EditMessageRequest();
        request.setContent("after");

        when(messageRepository.findById(20L)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, actorId)).thenReturn(true);
        when(messageRepository.save(message)).thenReturn(message);
        when(messageAttachmentRepository.findByMessageIdIn(List.of(20L))).thenReturn(List.of());
        when(messageReactionRepository.findByMessageIdIn(List.of(20L))).thenReturn(List.of());
        when(userProfileRepository.findById(actorId))
                .thenReturn(Optional.of(activeUser(actorId, "editor.user", "Editor Name", "https://cdn.example.com/editor.png")));

        MessageResponse response = messageService.editMessage(20L, actorId, request);

        assertThat(response.getContent()).isEqualTo("after");
        assertThat(response.getEditedAt()).isNotNull();
        assertThat(response.getSenderDisplayName()).isEqualTo("Editor Name");
        assertThat(response.getSenderAvatarUrl()).isEqualTo("https://cdn.example.com/editor.png");

        ArgumentCaptor<RealtimeEvent<?>> eventCaptor = ArgumentCaptor.forClass((Class) RealtimeEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/" + conversationId), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getPayload()).isInstanceOf(MessageResponse.class);
        MessageResponse realtimePayload = (MessageResponse) eventCaptor.getValue().getPayload();
        assertThat(realtimePayload.getSenderDisplayName()).isEqualTo("Editor Name");
        assertThat(realtimePayload.getSenderAvatarUrl()).isEqualTo("https://cdn.example.com/editor.png");
    }

    @Test
    void removeForMeDoesNotAffectGlobalDeleteSemantics() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Message message = new Message();
        message.setId(30L);
        message.setConversationId(conversationId);
        message.setSenderId(UUID.randomUUID());

        when(messageRepository.findById(30L)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);

        messageService.removeMessageForMe(30L, userId);

        assertThat(message.getDeletedAt()).isNull();
        verify(messageUserStateRepository).markDeletedForMe(eq(30L), eq(userId), any(Instant.class));
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void deleteMessageDoesNotUsePerUserState() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(senderId);

        Message message = new Message();
        message.setId(40L);
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setCreatedAt(Instant.now().plusSeconds(60));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findById(40L)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId)).thenReturn(List.of());
        when(messageRepository.save(message)).thenReturn(message);

        messageService.deleteMessage(40L, senderId);

        assertThat(message.getDeletedAt()).isNotNull();
        verifyNoInteractions(messageUserStateRepository);
    }

    @Test
    void sendMessageRejectsDeletedConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(senderId);
        conversation.setType(ConversationType.GROUP);
        conversation.setDeletedAt(Instant.now());

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> messageService.sendMessage(senderId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void sendMessageStillDeliversUnreadRefreshForNoneNotificationLevelMembers() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(senderId);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello");

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(101L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        ConversationUserSetting recipientSetting = new ConversationUserSetting();
        recipientSetting.setConversationId(conversationId);
        recipientSetting.setUserId(recipientId);
        recipientSetting.setNotificationLevel(ConversationNotificationLevel.NONE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(101L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(101L, senderId)).thenReturn(Optional.of(senderState));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, senderId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, recipientId))
                .thenReturn(Optional.of(recipientSetting));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(recipientId), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, recipientId)).thenReturn(4L);

        messageService.sendMessage(senderId, request);

        ArgumentCaptor<RealtimeEvent<?>> eventCaptor = ArgumentCaptor.forClass((Class) RealtimeEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + senderId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getPayload()).isInstanceOf(ConversationResponse.class);
        ConversationResponse response = (ConversationResponse) eventCaptor.getValue().getPayload();
        assertThat(response.getUnreadCount()).isEqualTo(4L);
    }

    @Test
    void sendMessageTreatsMentionsOnlyAsAllForCompatibilityRefresh() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.PRIVATE);
        conversation.setCreatorId(senderId);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello");

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(102L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        ConversationUserSetting recipientSetting = new ConversationUserSetting();
        recipientSetting.setConversationId(conversationId);
        recipientSetting.setUserId(recipientId);
        recipientSetting.setNotificationLevel(ConversationNotificationLevel.MENTIONS_ONLY);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(102L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(102L, senderId)).thenReturn(Optional.of(senderState));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, senderId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, recipientId))
                .thenReturn(Optional.of(recipientSetting));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(recipientId), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, recipientId)).thenReturn(0L);

        messageService.sendMessage(senderId, request);

        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void sendMessageDeliversUnreadRefreshToAllMentionsOnlyGroupMembers() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID mentionedUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.GROUP);
        conversation.setCreatorId(senderId);
        conversation.setName("Group");

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello @target and everyone else");

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(103L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        ConversationUserSetting mentionedSetting = new ConversationUserSetting();
        mentionedSetting.setConversationId(conversationId);
        mentionedSetting.setUserId(mentionedUserId);
        mentionedSetting.setNotificationLevel(ConversationNotificationLevel.MENTIONS_ONLY);

        ConversationUserSetting otherSetting = new ConversationUserSetting();
        otherSetting.setConversationId(conversationId);
        otherSetting.setUserId(otherUserId);
        otherSetting.setNotificationLevel(ConversationNotificationLevel.MENTIONS_ONLY);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(
                        member(conversationId, senderId),
                        member(conversationId, mentionedUserId),
                        member(conversationId, otherUserId)
                ))
                .thenReturn(List.of(
                        member(conversationId, senderId),
                        member(conversationId, mentionedUserId),
                        member(conversationId, otherUserId)
                ))
                .thenReturn(List.of(
                        member(conversationId, senderId),
                        member(conversationId, mentionedUserId),
                        member(conversationId, otherUserId)
                ));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(103L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(103L, senderId)).thenReturn(Optional.of(senderState));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, senderId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, mentionedUserId))
                .thenReturn(Optional.of(mentionedSetting));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, otherUserId))
                .thenReturn(Optional.of(otherSetting));
        when(userProfileRepository.findAllById(any(Iterable.class)))
                .thenReturn(List.of(
                        activeUser(senderId, "sender"),
                        activeUser(mentionedUserId, "target"),
                        activeUser(otherUserId, "other")
                ))
                .thenReturn(List.of(
                        activeUser(senderId, "sender"),
                        activeUser(mentionedUserId, "target"),
                        activeUser(otherUserId, "other")
                ));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(mentionedUserId), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(otherUserId), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, mentionedUserId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, otherUserId)).thenReturn(3L);

        messageService.sendMessage(senderId, request);

        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + mentionedUserId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + otherUserId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void sendMessageStillDeliversUnreadRefreshForMentionsOnlyGroupMemberWithoutMention() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.GROUP);
        conversation.setCreatorId(senderId);
        conversation.setName("Group");

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello @ghost");

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(104L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        ConversationUserSetting recipientSetting = new ConversationUserSetting();
        recipientSetting.setConversationId(conversationId);
        recipientSetting.setUserId(recipientId);
        recipientSetting.setNotificationLevel(ConversationNotificationLevel.MENTIONS_ONLY);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(104L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(104L, senderId)).thenReturn(Optional.of(senderState));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, senderId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, recipientId))
                .thenReturn(Optional.of(recipientSetting));
        when(userProfileRepository.findAllById(any(Iterable.class)))
                .thenReturn(List.of(activeUser(senderId, "sender"), activeUser(recipientId, "target")))
                .thenReturn(List.of(activeUser(senderId, "sender"), activeUser(recipientId, "target")));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(recipientId), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, recipientId)).thenReturn(2L);

        messageService.sendMessage(senderId, request);

        ArgumentCaptor<RealtimeEvent<?>> eventCaptor = ArgumentCaptor.forClass((Class) RealtimeEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getPayload()).isInstanceOf(ConversationResponse.class);
        ConversationResponse response = (ConversationResponse) eventCaptor.getValue().getPayload();
        assertThat(response.getUnreadCount()).isEqualTo(2L);
        assertThat(response.getMembers()).hasSize(2);
    }

    @Test
    void sendMessageMatchesMentionsCaseInsensitivelyForMentionsOnlyGroupMember() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setType(ConversationType.GROUP);
        conversation.setCreatorId(senderId);
        conversation.setName("Group");

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello @Target");

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(105L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        ConversationUserSetting recipientSetting = new ConversationUserSetting();
        recipientSetting.setConversationId(conversationId);
        recipientSetting.setUserId(recipientId);
        recipientSetting.setNotificationLevel(ConversationNotificationLevel.MENTIONS_ONLY);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(105L);
            return message;
        });
        when(messageUserStateRepository.findByMessageIdAndUserId(105L, senderId)).thenReturn(Optional.of(senderState));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, senderId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, recipientId))
                .thenReturn(Optional.of(recipientSetting));
        when(userProfileRepository.findAllById(any(Iterable.class)))
                .thenReturn(List.of(activeUser(senderId, "sender"), activeUser(recipientId, "target")));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(recipientId), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, recipientId)).thenReturn(0L);

        messageService.sendMessage(senderId, request);

        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
    }

    private ConversationMember member(UUID conversationId, UUID userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER);
        return member;
    }

    private UserProfile activeUser(UUID userId, String username) {
        return activeUser(userId, username, username, null);
    }

    private UserProfile activeUser(UUID userId, String username, String displayName, String avatarUrl) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);
        userProfile.setUsername(username);
        userProfile.setDisplayName(displayName);
        userProfile.setAvatarUrl(avatarUrl);
        return userProfile;
    }

    private MessageAttachmentPayload attachmentPayload(
            String url,
            String storageKey,
            String fileName,
            String contentType,
            long fileSize,
            MessageType type) {
        MessageAttachmentPayload payload = new MessageAttachmentPayload();
        payload.setUrl(url);
        payload.setStorageKey(storageKey);
        payload.setFileName(fileName);
        payload.setContentType(contentType);
        payload.setFileSize(fileSize);
        payload.setType(type);
        return payload;
    }

    private MessageAttachment savedAttachment(
            long messageId,
            String url,
            String storageKey,
            String fileName,
            String contentType,
            long fileSize,
            MessageType type) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessageId(messageId);
        attachment.setFileUrl(url);
        attachment.setStorageKey(storageKey);
        attachment.setOriginalFileName(fileName);
        attachment.setFileType(contentType);
        attachment.setFileSize(fileSize);
        attachment.setAttachmentType(type);
        return attachment;
    }
}
