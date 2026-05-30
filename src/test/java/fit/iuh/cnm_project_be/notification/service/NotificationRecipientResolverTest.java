package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.notification.dto.ResolvedNotificationRecipient;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationRecipientResolverTest {

    private final ConversationMemberRepository memberRepository = mock(ConversationMemberRepository.class);
    private final NotificationRecipientResolver resolver = new NotificationRecipientResolver(memberRepository);

    @Test
    void privateMessageReturnsOtherParticipant() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(memberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, recipientId)));

        List<UUID> result = resolver.resolvePrivateMessageRecipients(
                message(conversationId, senderId),
                conversation(conversationId, ConversationType.PRIVATE));

        assertThat(result).containsExactly(recipientId);
    }

    @Test
    void groupMessageReturnsActiveMembersExceptSender() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(memberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, first), member(conversationId, second)));

        List<UUID> result = resolver.resolveGroupMessageRecipients(
                message(conversationId, senderId),
                conversation(conversationId, ConversationType.GROUP));

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void groupMentionReturnsMentionedActiveMembersExceptSender() {
        UUID senderId = UUID.randomUUID();
        UUID mentioned = UUID.randomUUID();

        List<UUID> result = resolver.resolveGroupMentionRecipients(
                message(UUID.randomUUID(), senderId),
                List.of(senderId, mentioned));

        assertThat(result).containsExactly(mentioned);
    }

    @Test
    void replyReturnsOriginalAuthorExceptSender() {
        UUID senderId = UUID.randomUUID();
        UUID originalAuthor = UUID.randomUUID();
        Message message = message(UUID.randomUUID(), senderId);
        message.setReplyToSenderId(originalAuthor);

        assertThat(resolver.resolveReplyRecipients(message)).containsExactly(originalAuthor);
    }

    @Test
    void reactionReturnsOriginalAuthorExceptActor() {
        UUID author = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        assertThat(resolver.resolveReactionRecipients(message(UUID.randomUUID(), author), actor)).containsExactly(author);
    }

    @Test
    void groupCallStartedReturnsActiveMembersExceptInitiator() {
        UUID conversationId = UUID.randomUUID();
        UUID initiator = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        when(memberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, initiator), member(conversationId, invited)));

        assertThat(resolver.resolveGroupCallStartedRecipients(conversationId, initiator)).containsExactly(invited);
    }

    @Test
    void messageRecipientsPrioritizeMentionOverReplyAndGroupMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID mentionedAndReplied = UUID.randomUUID();
        Message message = message(conversationId, senderId);
        message.setReplyToSenderId(mentionedAndReplied);
        when(memberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, mentionedAndReplied)));

        List<ResolvedNotificationRecipient> result = resolver.resolveMessageRecipients(
                message,
                conversation(conversationId, ConversationType.GROUP),
                List.of(mentionedAndReplied));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(NotificationType.GROUP_MENTION);
    }

    @Test
    void messageRecipientsPrioritizeReplyOverPrivateMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID originalAuthor = UUID.randomUUID();
        Message message = message(conversationId, senderId);
        message.setReplyToSenderId(originalAuthor);
        when(memberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, senderId), member(conversationId, originalAuthor)));

        List<ResolvedNotificationRecipient> result = resolver.resolveMessageRecipients(
                message,
                conversation(conversationId, ConversationType.PRIVATE),
                List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(originalAuthor);
        assertThat(result.get(0).getType()).isEqualTo(NotificationType.REPLY_TO_MY_MESSAGE);
        assertThat(result.get(0).isReplyToRecipientMessage()).isTrue();
    }

    private Conversation conversation(UUID id, ConversationType type) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setCreatorId(UUID.randomUUID());
        conversation.setType(type);
        return conversation;
    }

    private Message message(UUID conversationId, UUID senderId) {
        Message message = new Message();
        message.setId(10L);
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        return message;
    }

    private ConversationMember member(UUID conversationId, UUID userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER);
        return member;
    }
}
