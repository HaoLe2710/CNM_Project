package fit.iuh.cnm_project_be.message.service;

import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.message.repository.MessageReactionRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import fit.iuh.cnm_project_be.user.repository.UserBlockRepository;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceBlockTest {

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
    private UserBlockRepository userBlockRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private S3MediaStorageService s3MediaStorageService;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendMessageRejectsBlockedPrivateConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Conversation conversation = privateConversation(conversationId, senderId);
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, senderId))
                .thenReturn(Optional.of(member(conversationId, senderId)));
        when(conversationMemberRepository.findPartnerUserId(conversationId, senderId))
                .thenReturn(Optional.of(recipientId));
        when(userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(senderId, recipientId))
                .thenReturn(true);

        assertThatThrownBy(() -> messageService.sendMessage(senderId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("blocked");

        verifyNoInteractions(messageRepository);
    }

    @Test
    void sendMessageAllowsPrivateConversationWhenNoActiveBlockExists() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Conversation conversation = privateConversation(conversationId, senderId);
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setContent("hello again");

        MessageUserState senderState = new MessageUserState();
        senderState.setMessageId(501L);
        senderState.setUserId(senderId);
        senderState.setSeenAt(Instant.now());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, senderId))
                .thenReturn(Optional.of(member(conversationId, senderId)));
        when(conversationMemberRepository.findPartnerUserId(conversationId, senderId))
                .thenReturn(Optional.of(recipientId));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(501L);
            return message;
        });
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)));
        when(messageUserStateRepository.findByMessageIdAndUserId(501L, senderId))
                .thenReturn(Optional.of(senderState));
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(senderId), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(recipientId), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, senderId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, recipientId)).thenReturn(0L);

        MessageResponse response = messageService.sendMessage(senderId, request);

        assertThat(response.getConversationId()).isEqualTo(conversationId);
        assertThat(response.getContent()).isEqualTo("hello again");
    }

    private Conversation privateConversation(UUID conversationId, UUID creatorId) {
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(creatorId);
        conversation.setType(ConversationType.PRIVATE);
        return conversation;
    }

    private ConversationMember member(UUID conversationId, UUID userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER);
        return member;
    }
}
