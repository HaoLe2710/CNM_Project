package fit.iuh.cnm_project_be.room.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageUserStateRepository messageUserStateRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void addMemberAddsUserToGroupConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(userProfileRepository.findById(newUserId)).thenReturn(Optional.of(activeUser(newUserId)));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, newUserId)).thenReturn(false);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(
                        member(conversationId, ownerId, MemberRole.OWNER),
                        member(conversationId, newUserId, MemberRole.MEMBER)
                ));
        when(messageUserStateRepository.countUnreadMessages(conversationId, ownerId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, newUserId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(any(), any(), any(), any(), any())).thenReturn(List.of());

        ConversationResponse response = conversationService.addMember(conversationId, ownerId, newUserId);

        assertThat(response.getId()).isEqualTo(conversationId);
        verify(conversationMemberRepository).save(any(ConversationMember.class));
    }

    @Test
    void addMemberRejectsPrivateConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(ownerId);
        conversation.setType(ConversationType.PRIVATE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));

        assertThatThrownBy(() -> conversationService.addMember(conversationId, ownerId, newUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only group conversations");
    }

    @Test
    void removeMemberRequiresManagerAuthorization() {
        UUID conversationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, memberId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, memberId))
                .thenReturn(Optional.of(member(conversationId, memberId, MemberRole.MEMBER)));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, targetId))
                .thenReturn(Optional.of(member(conversationId, targetId, MemberRole.MEMBER)));

        assertThatThrownBy(() -> conversationService.removeMember(conversationId, memberId, targetId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owners or admins");
    }

    @Test
    void leaveConversationRemovesNonOwnerMember() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, memberId))
                .thenReturn(Optional.of(member(conversationId, memberId, MemberRole.MEMBER)));
        when(conversationMemberRepository.countByConversationId(conversationId)).thenReturn(2L);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(messageUserStateRepository.countUnreadMessages(conversationId, ownerId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(any(), any(), any(), any(), any())).thenReturn(List.of());

        conversationService.leaveConversation(conversationId, memberId);

        verify(conversationMemberRepository).deleteByConversationIdAndUserId(conversationId, memberId);
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void ownerCannotLeaveWhileOtherMembersRemain() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(conversationMemberRepository.countByConversationId(conversationId)).thenReturn(2L);

        assertThatThrownBy(() -> conversationService.leaveConversation(conversationId, ownerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ownership transfer");
    }

    @Test
    void lastOwnerLeavingSoftDeletesConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(conversationMemberRepository.countByConversationId(conversationId)).thenReturn(1L);

        conversationService.leaveConversation(conversationId, ownerId);

        verify(conversationMemberRepository).deleteByConversationIdAndUserId(conversationId, ownerId);
        verify(conversationRepository).save(conversation);
        assertThat(conversation.isDeleted()).isTrue();
    }

    private Conversation groupConversation(UUID conversationId, UUID ownerId) {
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(ownerId);
        conversation.setType(ConversationType.GROUP);
        conversation.setName("Group");
        return conversation;
    }

    private ConversationMember member(UUID conversationId, UUID userId, MemberRole role) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private UserProfile activeUser(UUID userId) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);
        return userProfile;
    }
}
