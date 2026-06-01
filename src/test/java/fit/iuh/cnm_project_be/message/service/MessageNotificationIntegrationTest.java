//package fit.iuh.cnm_project_be.message.service;
//
//import fit.iuh.cnm_project_be.message.dto.MessageReactionRequest;
//import fit.iuh.cnm_project_be.message.dto.MessageAttachmentPayload;
//import fit.iuh.cnm_project_be.message.dto.MessageMentionPayload;
//import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
//import fit.iuh.cnm_project_be.message.entity.Message;
//import fit.iuh.cnm_project_be.message.entity.MessageReaction;
//import fit.iuh.cnm_project_be.message.enums.MessageReactionType;
//import fit.iuh.cnm_project_be.message.enums.MessageType;
//import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
//import fit.iuh.cnm_project_be.message.repository.ConversationMemberReadStateRepository;
//import fit.iuh.cnm_project_be.message.repository.MessageReactionRepository;
//import fit.iuh.cnm_project_be.message.repository.MessageRepository;
//import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
//import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
//import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
//import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
//import fit.iuh.cnm_project_be.notification.enums.NotificationType;
//import fit.iuh.cnm_project_be.notification.service.NotificationContentBuilder;
//import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
//import fit.iuh.cnm_project_be.notification.service.NotificationRecipientResolver;
//import fit.iuh.cnm_project_be.room.entity.Conversation;
//import fit.iuh.cnm_project_be.room.entity.ConversationMember;
//import fit.iuh.cnm_project_be.room.enums.ConversationType;
//import fit.iuh.cnm_project_be.room.enums.MemberRole;
//import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
//import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
//import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
//import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
//import fit.iuh.cnm_project_be.user.entity.UserProfile;
//import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.Pageable;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//
//import java.time.Instant;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.atLeastOnce;
//import static org.mockito.Mockito.lenient;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class MessageNotificationIntegrationTest {
//
//    @Mock
//    private MessageRepository messageRepository;
//    @Mock
//    private MessageAttachmentRepository messageAttachmentRepository;
//    @Mock
//    private MessageReactionRepository messageReactionRepository;
//    @Mock
//    private ConversationMemberReadStateRepository conversationMemberReadStateRepository;
//    @Mock
//    private MessageStatusRepository messageStatusRepository;
//    @Mock
//    private MessageUserStateRepository messageUserStateRepository;
//    @Mock
//    private ConversationRepository conversationRepository;
//    @Mock
//    private ConversationMemberRepository conversationMemberRepository;
//    @Mock
//    private ConversationUserSettingRepository conversationUserSettingRepository;
//    @Mock
//    private UserProfileRepository userProfileRepository;
//    @Mock
//    private SimpMessagingTemplate messagingTemplate;
//    @Mock
//    private S3MediaStorageService s3MediaStorageService;
//    @Mock
//    private NotificationDispatcher notificationDispatcher;
//
//    private MessageService messageService;
//
//    @BeforeEach
//    void setUp() {
//        messageService = new MessageService(
//                messageRepository,
//                messageAttachmentRepository,
//                messageReactionRepository,
//                conversationMemberReadStateRepository,
//                messageStatusRepository,
//                messageUserStateRepository,
//                conversationRepository,
//                conversationMemberRepository,
//                conversationUserSettingRepository,
//                userProfileRepository,
//                messagingTemplate,
//                s3MediaStorageService,
//                notificationDispatcher,
//                new NotificationRecipientResolver(conversationMemberRepository),
//                new NotificationContentBuilder()
//        );
//        lenient().when(notificationDispatcher.dispatch(any())).thenReturn(dispatchResult());
//        lenient().when(messageAttachmentRepository.findByMessageIdIn(any())).thenReturn(List.of());
//        lenient().when(messageReactionRepository.findByMessageIdIn(any())).thenReturn(List.of());
//        lenient().when(messageRepository.findVisibleMessages(any(), any(), any(Pageable.class))).thenReturn(List.of());
//        lenient().when(conversationUserSettingRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());
//    }
//
//    @Test
//    void sendPrivateMessageDispatchesRecipientNotification() {
//        UUID conversationId = UUID.randomUUID();
//        UUID senderId = UUID.randomUUID();
//        UUID recipientId = UUID.randomUUID();
//        mockConversation(conversationId, senderId, ConversationType.PRIVATE, List.of(senderId, recipientId));
//
//        messageService.sendMessage(senderId, sendRequest(conversationId, "hello @nobody", null));
//
//        NotificationDispatchRequest request = captureSingleDispatch();
//        assertThat(request.getType()).isEqualTo(NotificationType.NEW_PRIVATE_MESSAGE);
//        assertThat(request.getExplicitRecipientIds()).containsExactly(recipientId);
//        assertThat(request.getDedupKeyPrefix()).isEqualTo("NEW_PRIVATE_MESSAGE:100");
//    }
//
//    @Test
//    void groupMentionDoesNotAlsoCreateNormalGroupNotificationForMentionedRecipient() {
//        UUID conversationId = UUID.randomUUID();
//        UUID senderId = UUID.randomUUID();
//        UUID mentionedId = UUID.randomUUID();
//        UUID normalRecipientId = UUID.randomUUID();
//        mockConversation(conversationId, senderId, ConversationType.GROUP, List.of(senderId, mentionedId, normalRecipientId));
//        when(userProfileRepository.findAllById(any())).thenReturn(List.of(
//                profile(senderId, "sender"),
//                profile(mentionedId, "bob"),
//                profile(normalRecipientId, "cindy")
//        ));
//
//        messageService.sendMessage(senderId, sendRequest(conversationId, "hello @bob", null));
//
//        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
//        verify(notificationDispatcher, atLeastOnce()).dispatch(captor.capture());
//        List<NotificationDispatchRequest> requests = captor.getAllValues();
//        assertThat(requests).anySatisfy(request -> {
//            assertThat(request.getType()).isEqualTo(NotificationType.GROUP_MENTION);
//            assertThat(request.getExplicitRecipientIds()).containsExactly(mentionedId);
//        });
//        assertThat(requests).anySatisfy(request -> {
//            assertThat(request.getType()).isEqualTo(NotificationType.NEW_GROUP_MESSAGE);
//            assertThat(request.getExplicitRecipientIds()).containsExactly(normalRecipientId);
//        });
//    }
//
//    @Test
//    void sendReplyCreatesReplyNotificationForOriginalAuthor() {
//        UUID conversationId = UUID.randomUUID();
//        UUID senderId = UUID.randomUUID();
//        UUID originalAuthorId = UUID.randomUUID();
//        mockConversation(conversationId, senderId, ConversationType.GROUP, List.of(senderId, originalAuthorId));
//        Message replied = message(conversationId, originalAuthorId, "old");
//        replied.setId(77L);
//        when(messageRepository.findByIdAndDeletedAtIsNull(77L)).thenReturn(Optional.of(replied));
//
//        messageService.sendMessage(senderId, sendRequest(conversationId, "reply", 77L));
//
//        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
//        verify(notificationDispatcher, atLeastOnce()).dispatch(captor.capture());
//        assertThat(captor.getAllValues()).anySatisfy(request -> {
//            assertThat(request.getType()).isEqualTo(NotificationType.REPLY_TO_MY_MESSAGE);
//            assertThat(request.getExplicitRecipientIds()).containsExactly(originalAuthorId);
//            assertThat(request.isReplyToRecipientMessage()).isTrue();
//        });
//    }
//
//    @Test
//    void groupMentionFromExplicitPayloadDispatchesMentionNotification() {
//        UUID conversationId = UUID.randomUUID();
//        UUID senderId = UUID.randomUUID();
//        UUID mentionedId = UUID.randomUUID();
//        UUID normalRecipientId = UUID.randomUUID();
//        mockConversation(conversationId, senderId, ConversationType.GROUP, List.of(senderId, mentionedId, normalRecipientId));
//
//        MessageMentionPayload mentionPayload = new MessageMentionPayload();
//        mentionPayload.setUserId(mentionedId);
//        mentionPayload.setDisplayName("Mentioned");
//
//        SendMessageRequest request = sendRequest(conversationId, "hello @display-name", null);
//        request.setMentions(List.of(mentionPayload));
//
//        messageService.sendMessage(senderId, request);
//
//        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
//        verify(notificationDispatcher, atLeastOnce()).dispatch(captor.capture());
//        List<NotificationDispatchRequest> requests = captor.getAllValues();
//        assertThat(requests).anySatisfy(dispatchRequest -> {
//            assertThat(dispatchRequest.getType()).isEqualTo(NotificationType.GROUP_MENTION);
//            assertThat(dispatchRequest.getExplicitRecipientIds()).containsExactly(mentionedId);
//        });
//        assertThat(requests).anySatisfy(dispatchRequest -> {
//            assertThat(dispatchRequest.getType()).isEqualTo(NotificationType.NEW_GROUP_MESSAGE);
//            assertThat(dispatchRequest.getExplicitRecipientIds()).containsExactly(normalRecipientId);
//        });
//    }
//
//    @Test
//    void reactionCreatesReactionNotificationForOriginalAuthor() {
//        UUID conversationId = UUID.randomUUID();
//        UUID authorId = UUID.randomUUID();
//        UUID actorId = UUID.randomUUID();
//        Message message = message(conversationId, authorId, "hello");
//        message.setId(5L);
//        mockConversation(conversationId, actorId, ConversationType.PRIVATE, List.of(authorId, actorId));
//        when(messageRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(message));
//        when(messageReactionRepository.findByMessageIdAndUserId(5L, actorId)).thenReturn(Optional.empty());
//        when(messageReactionRepository.save(any(MessageReaction.class))).thenAnswer(invocation -> {
//            MessageReaction reaction = invocation.getArgument(0);
//            reaction.setId(99L);
//            return reaction;
//        });
//
//        MessageReactionRequest request = new MessageReactionRequest();
//        request.setReactionType(MessageReactionType.LIKE);
//        messageService.addOrUpdateReaction(5L, actorId, request);
//
//        NotificationDispatchRequest dispatch = captureSingleDispatch();
//        assertThat(dispatch.getType()).isEqualTo(NotificationType.REACTION_TO_MY_MESSAGE);
//        assertThat(dispatch.getExplicitRecipientIds()).containsExactly(authorId);
//        assertThat(dispatch.getDedupKeyPrefix()).isEqualTo("REACTION_TO_MY_MESSAGE:99");
//    }
//
//    @Test
//    void sendAudioMessageDispatchesVoicePreviewNotification() {
//        UUID conversationId = UUID.randomUUID();
//        UUID senderId = UUID.randomUUID();
//        UUID recipientId = UUID.randomUUID();
//        mockConversation(conversationId, senderId, ConversationType.PRIVATE, List.of(senderId, recipientId));
//
//        messageService.sendMessage(senderId, audioRequest(conversationId));
//
//        NotificationDispatchRequest request = captureSingleDispatch();
//        assertThat(request.getType()).isEqualTo(NotificationType.NEW_PRIVATE_MESSAGE);
//        assertThat(request.getExplicitRecipientIds()).containsExactly(recipientId);
//        assertThat(request.getMetadata())
//                .containsEntry("messagePreview", "Đã gửi một tin nhắn thoại");
//    }
//
//    @Test
//    void notificationFailureDoesNotFailMessageSend() {
//        UUID conversationId = UUID.randomUUID();
//        UUID senderId = UUID.randomUUID();
//        UUID recipientId = UUID.randomUUID();
//        mockConversation(conversationId, senderId, ConversationType.PRIVATE, List.of(senderId, recipientId));
//        when(notificationDispatcher.dispatch(any())).thenThrow(new RuntimeException("notification down"));
//
//        messageService.sendMessage(senderId, sendRequest(conversationId, "hello", null));
//    }
//
//    private NotificationDispatchRequest captureSingleDispatch() {
//        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
//        verify(notificationDispatcher).dispatch(captor.capture());
//        return captor.getValue();
//    }
//
//    private void mockConversation(UUID conversationId, UUID senderId, ConversationType type, List<UUID> memberIds) {
//        Conversation conversation = new Conversation();
//        conversation.setId(conversationId);
//        conversation.setCreatorId(senderId);
//        conversation.setType(type);
//        conversation.setName(type == ConversationType.GROUP ? "Group" : null);
//        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
//        when(conversationMemberRepository.findByConversationIdAndUserId(eq(conversationId), any()))
//                .thenAnswer(invocation -> Optional.of(member(conversationId, invocation.getArgument(1))));
//        lenient().when(conversationMemberRepository.findByConversationId(conversationId))
//                .thenReturn(memberIds.stream().map(userId -> member(conversationId, userId)).toList());
//        memberIds.forEach(userId -> lenient().when(userProfileRepository.findById(userId))
//                .thenReturn(Optional.of(profile(userId, "user-" + userId.toString().substring(0, 4)))));
//        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
//            Message message = invocation.getArgument(0);
//            message.setId(100L);
//            message.setCreatedAt(Instant.now());
//            return message;
//        });
//    }
//
//    private SendMessageRequest sendRequest(UUID conversationId, String content, Long replyToMessageId) {
//        SendMessageRequest request = new SendMessageRequest();
//        request.setConversationId(conversationId);
//        request.setContent(content);
//        request.setReplyToMessageId(replyToMessageId);
//        return request;
//    }
//
//    private SendMessageRequest audioRequest(UUID conversationId) {
//        MessageAttachmentPayload payload = new MessageAttachmentPayload();
//        payload.setUrl("https://cdn.example.com/chat/voice-1.webm");
//        payload.setStorageKey("chat/u1/voice-1.webm");
//        payload.setFileName("voice-1.webm");
//        payload.setContentType("audio/webm");
//        payload.setFileSize(30_000L);
//        payload.setType(MessageType.AUDIO);
//
//        SendMessageRequest request = new SendMessageRequest();
//        request.setConversationId(conversationId);
//        request.setMessageType(MessageType.AUDIO);
//        request.setAttachments(List.of(payload));
//        return request;
//    }
//
//    private Message message(UUID conversationId, UUID senderId, String content) {
//        Message message = new Message();
//        message.setConversationId(conversationId);
//        message.setSenderId(senderId);
//        message.setContent(content);
//        message.setMessageType(MessageType.TEXT);
//        message.setCreatedAt(Instant.now());
//        return message;
//    }
//
//    private ConversationMember member(UUID conversationId, UUID userId) {
//        ConversationMember member = new ConversationMember();
//        member.setConversationId(conversationId);
//        member.setUserId(userId);
//        member.setRole(MemberRole.MEMBER);
//        return member;
//    }
//
//    private UserProfile profile(UUID userId, String username) {
//        UserProfile profile = new UserProfile();
//        profile.setUserId(userId);
//        profile.setUsername(username);
//        profile.setDisplayName(username);
//        return profile;
//    }
//
//    private NotificationDispatchResult dispatchResult() {
//        return NotificationDispatchResult.builder()
//                .candidateRecipientCount(1)
//                .policyAllowedInAppCount(1)
//                .policyAllowedPushCount(1)
//                .createdNotificationCount(1)
//                .pushSuccessCount(1)
//                .pushFailureCount(0)
//                .deniedRecipients(java.util.Map.of())
//                .createdNotificationIds(List.of(UUID.randomUUID()))
//                .errors(List.of())
//                .build();
//    }
//}
