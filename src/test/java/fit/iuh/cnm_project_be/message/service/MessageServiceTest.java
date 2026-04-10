package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.EditMessageRequest;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.message.repository.MessageReactionRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
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
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;
    @Mock
    private MessageReactionRepository messageReactionRepository;
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
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of());
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
    void markAsSeenUpdatesOnlyMessageUserStates() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationMemberRepository.findByConversationId(conversationId)).thenReturn(List.of());

        messageService.markAsSeen(conversationId, userId);

        verify(messageUserStateRepository).markConversationAsSeen(eq(conversationId), eq(userId), any(Instant.class), any(Instant.class));
        verifyNoInteractions(messageStatusRepository);
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
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(userId), eq(null), eq(null), any()))
                .thenReturn(List.of(message));
        when(messageAttachmentRepository.findByMessageIdIn(List.of(10L))).thenReturn(List.of());
        when(messageReactionRepository.findByMessageIdIn(List.of(10L))).thenReturn(List.of());
        when(messageUserStateRepository.findByMessageIdInAndUserId(List.of(10L), userId)).thenReturn(List.of(state));

        MessageResponse response = messageService.getMessages(conversationId, userId, null, 50).getItems().get(0);

        assertThat(response.getSeen()).isTrue();
        verifyNoInteractions(messageStatusRepository);
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

        MessageResponse response = messageService.editMessage(20L, actorId, request);

        assertThat(response.getContent()).isEqualTo("after");
        assertThat(response.getEditedAt()).isNotNull();
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
    void sendMessageSuppressesConversationRefreshForNoneNotificationLevelMembers() {
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
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);

        messageService.sendMessage(senderId, request);

        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + senderId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
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
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any(), any(), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(recipientId), any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, recipientId)).thenReturn(0L);

        messageService.sendMessage(senderId, request);

        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void sendMessageDeliversMentionsOnlyRefreshForMentionedGroupMember() {
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
                ));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any(), any(), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(mentionedUserId), any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, mentionedUserId)).thenReturn(0L);

        messageService.sendMessage(senderId, request);

        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + mentionedUserId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/users/" + otherUserId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void sendMessageSuppressesMentionsOnlyRefreshForUnknownUsername() {
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
                .thenReturn(List.of(activeUser(senderId, "sender"), activeUser(recipientId, "target")));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);

        messageService.sendMessage(senderId, request);

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
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
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any(), any(), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(recipientId), any(), any(), any())).thenReturn(List.of());
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
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);
        userProfile.setUsername(username);
        return userProfile;
    }
}
