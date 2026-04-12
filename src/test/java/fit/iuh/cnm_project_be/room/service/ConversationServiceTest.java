package fit.iuh.cnm_project_be.room.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.dto.CreateConversationRequest;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private ConversationUserSettingRepository conversationUserSettingRepository;
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
    void createConversationReusesExistingPrivateConversation() {
        UUID creatorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(creatorId);
        conversation.setType(ConversationType.PRIVATE);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setType(ConversationType.PRIVATE);
        request.setParticipantIds(List.of(otherUserId));

        when(userProfileRepository.findById(otherUserId))
                .thenReturn(Optional.of(activeUser(otherUserId, "Teammate", "https://cdn.example.com/teammate.png")));
        when(conversationRepository.findPrivateConversationByParticipants(creatorId, otherUserId))
                .thenReturn(Optional.of(conversation));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, creatorId))
                .thenReturn(Optional.empty());
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(
                        member(conversationId, creatorId, MemberRole.OWNER),
                        member(conversationId, otherUserId, MemberRole.MEMBER)
                ));
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, creatorId)).thenReturn(0L);

        ConversationResponse response = conversationService.createConversation(creatorId, request);

        assertThat(response.getId()).isEqualTo(conversationId);
        assertThat(response.getDisplayName()).isEqualTo("Teammate");
        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn.example.com/teammate.png");
        assertThat(response.getPeerUserId()).isEqualTo(otherUserId);
        assertThat(response.getPeerDisplayName()).isEqualTo("Teammate");
        assertThat(response.getPeerAvatarUrl()).isEqualTo("https://cdn.example.com/teammate.png");
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void createConversationReturnsPrivatePeerMetadataForCreator() {
        UUID creatorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation savedConversation = new Conversation();
        savedConversation.setId(conversationId);
        savedConversation.setCreatorId(creatorId);
        savedConversation.setType(ConversationType.PRIVATE);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setType(ConversationType.PRIVATE);
        request.setParticipantIds(List.of(otherUserId));

        when(userProfileRepository.findById(creatorId))
                .thenReturn(Optional.of(activeUser(creatorId, "Creator", "https://cdn.example.com/creator.png")));
        when(userProfileRepository.findById(otherUserId))
                .thenReturn(Optional.of(activeUser(otherUserId, "Target User", "https://cdn.example.com/target.png")));
        when(conversationRepository.findPrivateConversationByParticipants(creatorId, otherUserId))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(savedConversation);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(
                        member(conversationId, creatorId, MemberRole.OWNER),
                        member(conversationId, otherUserId, MemberRole.MEMBER)
                ));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, creatorId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, otherUserId))
                .thenReturn(Optional.empty());
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, creatorId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, otherUserId)).thenReturn(0L);

        ConversationResponse response = conversationService.createConversation(creatorId, request);

        assertThat(response.getId()).isEqualTo(conversationId);
        assertThat(response.getDisplayName()).isEqualTo("Target User");
        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn.example.com/target.png");
        assertThat(response.getPeerUserId()).isEqualTo(otherUserId);
        assertThat(response.getPeerDisplayName()).isEqualTo("Target User");
        assertThat(response.getPeerAvatarUrl()).isEqualTo("https://cdn.example.com/target.png");
        verify(conversationMemberRepository, org.mockito.Mockito.times(2))
                .save(any(ConversationMember.class));
    }

    @Test
    void getMyConversationsKeepsGroupDisplayMetadataOnConversationFields() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);
        conversation.setName("Project Group");
        conversation.setAvatarUrl("https://cdn.example.com/group.png");

        when(conversationRepository.findAllByMemberId(userId)).thenReturn(List.of(conversation));
        when(conversationUserSettingRepository.findByUserIdAndConversationIdIn(any(), any()))
                .thenReturn(List.of());
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, userId)).thenReturn(0L);

        ConversationResponse response = conversationService.getMyConversations(userId, false).get(0);

        assertThat(response.getDisplayName()).isEqualTo("Project Group");
        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn.example.com/group.png");
        assertThat(response.getPeerUserId()).isNull();
        assertThat(response.getPeerDisplayName()).isNull();
        assertThat(response.getPeerAvatarUrl()).isNull();
    }

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
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());

        ConversationResponse response = conversationService.addMember(conversationId, ownerId, newUserId);

        assertThat(response.getId()).isEqualTo(conversationId);
        verify(conversationMemberRepository).save(any(ConversationMember.class));
    }

    @Test
    void renameConversationUpdatesGroupMetadata() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(conversationRepository.save(conversation)).thenReturn(conversation);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(messageUserStateRepository.countUnreadMessages(conversationId, ownerId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());

        ConversationResponse response = conversationService.renameConversation(conversationId, ownerId, "  New group name  ");

        assertThat(response.getName()).isEqualTo("New group name");
        assertThat(conversation.getName()).isEqualTo("New group name");
        verify(conversationRepository).save(conversation);
    }

    @Test
    void renameConversationRejectsPrivateConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(ownerId);
        conversation.setType(ConversationType.PRIVATE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));

        assertThatThrownBy(() -> conversationService.renameConversation(conversationId, ownerId, "Renamed"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only group conversations");
    }

    @Test
    void renameConversationRequiresPrivilegedMember() {
        UUID conversationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, memberId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, memberId))
                .thenReturn(Optional.of(member(conversationId, memberId, MemberRole.MEMBER)));

        assertThatThrownBy(() -> conversationService.renameConversation(conversationId, memberId, "Renamed"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owners or admins");
    }

    @Test
    void updateConversationAvatarUpdatesGroupMetadata() {
        UUID conversationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, adminId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, adminId))
                .thenReturn(Optional.of(member(conversationId, adminId, MemberRole.ADMIN)));
        when(conversationRepository.save(conversation)).thenReturn(conversation);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, adminId, MemberRole.ADMIN)));
        when(messageUserStateRepository.countUnreadMessages(conversationId, adminId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());

        ConversationResponse response = conversationService.updateConversationAvatar(
                conversationId,
                adminId,
                "  https://cdn.example.com/group.png  "
        );

        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn.example.com/group.png");
        assertThat(conversation.getAvatarUrl()).isEqualTo("https://cdn.example.com/group.png");
        verify(conversationRepository).save(conversation);
    }

    @Test
    void updateConversationAvatarRejectsUnchangedValue() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);
        conversation.setAvatarUrl("https://cdn.example.com/group.png");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));

        assertThatThrownBy(() -> conversationService.updateConversationAvatar(
                conversationId,
                ownerId,
                " https://cdn.example.com/group.png "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unchanged");
    }

    @Test
    void transferOwnershipMovesOwnerRoleToTargetMember() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);
        ConversationMember owner = member(conversationId, ownerId, MemberRole.OWNER);
        ConversationMember target = member(conversationId, targetId, MemberRole.MEMBER);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(owner));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, targetId))
                .thenReturn(Optional.of(target));
        when(userProfileRepository.findById(targetId)).thenReturn(Optional.of(activeUser(targetId)));
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(owner, target));
        when(messageUserStateRepository.countUnreadMessages(conversationId, ownerId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, targetId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());

        conversationService.transferOwnership(conversationId, ownerId, targetId);

        assertThat(owner.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(target.getRole()).isEqualTo(MemberRole.OWNER);
        verify(conversationMemberRepository).save(owner);
        verify(conversationMemberRepository).save(target);
    }

    @Test
    void transferOwnershipRejectsPrivateConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(ownerId);
        conversation.setType(ConversationType.PRIVATE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));

        assertThatThrownBy(() -> conversationService.transferOwnership(conversationId, ownerId, targetId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only group conversations");
    }

    @Test
    void transferOwnershipRequiresCurrentOwner() {
        UUID conversationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, adminId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, adminId))
                .thenReturn(Optional.of(member(conversationId, adminId, MemberRole.ADMIN)));

        assertThatThrownBy(() -> conversationService.transferOwnership(conversationId, adminId, targetId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void transferOwnershipRejectsNonMemberTarget() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, targetId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.transferOwnership(conversationId, ownerId, targetId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void transferOwnershipRejectsSelfTransfer() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);
        ConversationMember owner = member(conversationId, ownerId, MemberRole.OWNER);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> conversationService.transferOwnership(conversationId, ownerId, ownerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void promoteToAdminPromotesMember() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);
        ConversationMember owner = member(conversationId, ownerId, MemberRole.OWNER);
        ConversationMember target = member(conversationId, targetId, MemberRole.MEMBER);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(owner));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, targetId))
                .thenReturn(Optional.of(target));
        when(userProfileRepository.findById(targetId)).thenReturn(Optional.of(activeUser(targetId)));
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(owner, target));
        when(messageUserStateRepository.countUnreadMessages(conversationId, ownerId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, targetId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());

        conversationService.promoteToAdmin(conversationId, ownerId, targetId);

        assertThat(target.getRole()).isEqualTo(MemberRole.ADMIN);
        verify(conversationMemberRepository).save(target);
    }

    @Test
    void demoteAdminDemotesAdminToMember() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);
        ConversationMember owner = member(conversationId, ownerId, MemberRole.OWNER);
        ConversationMember target = member(conversationId, targetId, MemberRole.ADMIN);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(owner));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, targetId))
                .thenReturn(Optional.of(target));
        when(userProfileRepository.findById(targetId)).thenReturn(Optional.of(activeUser(targetId)));
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(owner, target));
        when(messageUserStateRepository.countUnreadMessages(conversationId, ownerId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, targetId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());

        conversationService.demoteAdmin(conversationId, ownerId, targetId);

        assertThat(target.getRole()).isEqualTo(MemberRole.MEMBER);
        verify(conversationMemberRepository).save(target);
    }

    @Test
    void closeConversationSoftDeletesGroupOwnedByActor() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(
                        member(conversationId, ownerId, MemberRole.OWNER),
                        member(conversationId, memberId, MemberRole.MEMBER)
                ));

        conversationService.closeConversation(conversationId, ownerId);

        assertThat(conversation.isDeleted()).isTrue();
        verify(conversationRepository).save(conversation);
    }

    @Test
    void updateMutePreferencePersistsMutedStateForCurrentMember() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(member(conversationId, userId, MemberRole.MEMBER)));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        conversationService.updateMutePreference(conversationId, userId, true);

        verify(conversationUserSettingRepository).save(any(ConversationUserSetting.class));
    }

    @Test
    void updateArchivePreferencePersistsArchivedStateForCurrentMember() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(member(conversationId, userId, MemberRole.MEMBER)));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        conversationService.updateArchivePreference(conversationId, userId, true);

        verify(conversationUserSettingRepository).save(any(ConversationUserSetting.class));
    }

    @Test
    void updatePinPreferencePersistsPinnedStateForCurrentMember() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(member(conversationId, userId, MemberRole.MEMBER)));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        conversationService.updatePinPreference(conversationId, userId, true);

        verify(conversationUserSettingRepository).save(any(ConversationUserSetting.class));
    }

    @Test
    void updateNotificationLevelPersistsForPrivateConversationMember() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(userId);
        conversation.setType(ConversationType.PRIVATE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(member(conversationId, userId, MemberRole.MEMBER)));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        conversationService.updateNotificationLevel(conversationId, userId, ConversationNotificationLevel.MENTIONS_ONLY);

        verify(conversationUserSettingRepository).save(any(ConversationUserSetting.class));
    }

    @Test
    void updateNotificationLevelNoOpsWhenUnchanged() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);
        ConversationUserSetting setting = new ConversationUserSetting();
        setting.setConversationId(conversationId);
        setting.setUserId(userId);
        setting.setNotificationLevel(ConversationNotificationLevel.NONE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(member(conversationId, userId, MemberRole.MEMBER)));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.of(setting));

        conversationService.updateNotificationLevel(conversationId, userId, ConversationNotificationLevel.NONE);

        verify(conversationUserSettingRepository, never()).save(any(ConversationUserSetting.class));
    }

    @Test
    void updateCustomNamePersistsTrimmedAliasForGroupConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(member(conversationId, userId, MemberRole.MEMBER)));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        conversationService.updateCustomName(conversationId, userId, "  My Alias  ");

        verify(conversationUserSettingRepository).save(any(ConversationUserSetting.class));
    }

    @Test
    void updateCustomNameClearsAliasWithBlankInput() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);
        ConversationUserSetting setting = new ConversationUserSetting();
        setting.setConversationId(conversationId);
        setting.setUserId(userId);
        setting.setCustomName("Alias");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(member(conversationId, userId, MemberRole.MEMBER)));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.of(setting));

        conversationService.updateCustomName(conversationId, userId, "   ");

        assertThat(setting.getCustomName()).isNull();
        verify(conversationUserSettingRepository).save(setting);
    }

    @Test
    void roomPreferenceRejectsNonMember() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.updateMutePreference(conversationId, userId, true))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void roomPreferenceRejectsDeletedConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);
        conversation.setDeletedAt(java.time.Instant.now());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> conversationService.updateArchivePreference(conversationId, userId, true))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void conversationResponseIncludesNotificationLevelCustomNameAndDisplayName() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);
        conversation.setName("Group");

        ConversationUserSetting setting = new ConversationUserSetting();
        setting.setConversationId(conversationId);
        setting.setUserId(userId);
        setting.setNotificationLevel(ConversationNotificationLevel.MENTIONS_ONLY);
        setting.setCustomName("Alias");

        when(conversationRepository.findAllByMemberId(userId)).thenReturn(List.of(conversation));
        when(conversationUserSettingRepository.findByUserIdAndConversationIdIn(any(), any()))
                .thenReturn(List.of(setting));
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(any(), any())).thenReturn(0L);

        ConversationResponse response = conversationService.getMyConversations(userId, false).get(0);

        assertThat(response.getNotificationLevel()).isEqualTo(ConversationNotificationLevel.MENTIONS_ONLY);
        assertThat(response.getCustomName()).isEqualTo("Alias");
        assertThat(response.getDisplayName()).isEqualTo("Alias");
    }

    @Test
    void conversationResponseFallsBackDisplayNameAndNotificationLevelDefaults() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);
        conversation.setName("Group");

        when(conversationRepository.findAllByMemberId(userId)).thenReturn(List.of(conversation));
        when(conversationUserSettingRepository.findByUserIdAndConversationIdIn(any(), any()))
                .thenReturn(List.of());
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(any(), any())).thenReturn(0L);

        ConversationResponse response = conversationService.getMyConversations(userId, false).get(0);

        assertThat(response.getNotificationLevel()).isEqualTo(ConversationNotificationLevel.ALL);
        assertThat(response.getCustomName()).isNull();
        assertThat(response.getDisplayName()).isEqualTo("Group");
    }

    @Test
    void renameConversationSuppressesRefreshForNoneNotificationLevelMembers() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        ConversationUserSetting recipientSetting = new ConversationUserSetting();
        recipientSetting.setConversationId(conversationId);
        recipientSetting.setUserId(recipientId);
        recipientSetting.setNotificationLevel(ConversationNotificationLevel.NONE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(conversationRepository.save(conversation)).thenReturn(conversation);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(
                        member(conversationId, ownerId, MemberRole.OWNER),
                        member(conversationId, recipientId, MemberRole.MEMBER)
                ));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, recipientId))
                .thenReturn(Optional.of(recipientSetting));
        when(messageUserStateRepository.countUnreadMessages(conversationId, ownerId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(ownerId), any())).thenReturn(List.of());

        conversationService.renameConversation(conversationId, ownerId, "Updated");

        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + ownerId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void renameConversationKeepsMentionsOnlyRefreshBehaviorForCompatibility() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        ConversationUserSetting recipientSetting = new ConversationUserSetting();
        recipientSetting.setConversationId(conversationId);
        recipientSetting.setUserId(recipientId);
        recipientSetting.setNotificationLevel(ConversationNotificationLevel.MENTIONS_ONLY);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(conversationRepository.save(conversation)).thenReturn(conversation);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(
                        member(conversationId, ownerId, MemberRole.OWNER),
                        member(conversationId, recipientId, MemberRole.MEMBER)
                ));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, recipientId))
                .thenReturn(Optional.of(recipientSetting));
        when(messageUserStateRepository.countUnreadMessages(conversationId, ownerId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, recipientId)).thenReturn(0L);
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(ownerId), any())).thenReturn(List.of());
        when(messageRepository.findVisibleMessages(eq(conversationId), eq(recipientId), any())).thenReturn(List.of());

        conversationService.renameConversation(conversationId, ownerId, "Updated");

        verify(messagingTemplate).convertAndSend(eq("/topic/users/" + recipientId + "/conversations"), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void notificationLevelDoesNotAffectConversationReadAccess() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);

        ConversationUserSetting setting = new ConversationUserSetting();
        setting.setConversationId(conversationId);
        setting.setUserId(userId);
        setting.setNotificationLevel(ConversationNotificationLevel.NONE);

        when(conversationRepository.findAllByMemberId(userId)).thenReturn(List.of(conversation));
        when(conversationUserSettingRepository.findByUserIdAndConversationIdIn(any(), any()))
                .thenReturn(List.of(setting));
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(any(), any())).thenReturn(0L);

        List<ConversationResponse> responses = conversationService.getMyConversations(userId, false);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getNotificationLevel()).isEqualTo(ConversationNotificationLevel.NONE);
    }

    @Test
    void getMyConversationsFiltersArchivedAndPinsPinnedFirst() {
        UUID userId = UUID.randomUUID();
        UUID archivedConversationId = UUID.randomUUID();
        UUID pinnedConversationId = UUID.randomUUID();
        UUID normalConversationId = UUID.randomUUID();

        Conversation archivedConversation = groupConversation(archivedConversationId, userId);
        archivedConversation.setCreatedAt(java.time.Instant.parse("2026-04-01T10:00:00Z"));
        Conversation pinnedConversation = groupConversation(pinnedConversationId, userId);
        pinnedConversation.setCreatedAt(java.time.Instant.parse("2026-04-01T09:00:00Z"));
        Conversation normalConversation = groupConversation(normalConversationId, userId);
        normalConversation.setCreatedAt(java.time.Instant.parse("2026-04-01T11:00:00Z"));

        ConversationUserSetting archivedSetting = new ConversationUserSetting();
        archivedSetting.setConversationId(archivedConversationId);
        archivedSetting.setUserId(userId);
        archivedSetting.setArchivedAt(java.time.Instant.parse("2026-04-01T12:00:00Z"));

        ConversationUserSetting pinnedSetting = new ConversationUserSetting();
        pinnedSetting.setConversationId(pinnedConversationId);
        pinnedSetting.setUserId(userId);
        pinnedSetting.setPinnedAt(java.time.Instant.parse("2026-04-01T12:05:00Z"));

        when(conversationRepository.findAllByMemberId(userId))
                .thenReturn(List.of(archivedConversation, pinnedConversation, normalConversation));
        when(conversationUserSettingRepository.findByUserIdAndConversationIdIn(any(), any()))
                .thenReturn(List.of(archivedSetting, pinnedSetting));
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(any(), any())).thenReturn(0L);

        List<ConversationResponse> responses = conversationService.getMyConversations(userId, false);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo(pinnedConversationId);
        assertThat(responses.get(0).isPinned()).isTrue();
        assertThat(responses.get(0).isArchived()).isFalse();
        assertThat(responses).extracting(ConversationResponse::getId)
                .doesNotContain(archivedConversationId);
    }

    @Test
    void conversationResponseIncludesRoomPreferenceFlagsForCurrentUser() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, userId);

        ConversationUserSetting setting = new ConversationUserSetting();
        setting.setConversationId(conversationId);
        setting.setUserId(userId);
        setting.setMutedAt(java.time.Instant.parse("2026-04-01T12:10:00Z"));
        setting.setArchivedAt(java.time.Instant.parse("2026-04-01T12:11:00Z"));
        setting.setPinnedAt(java.time.Instant.parse("2026-04-01T12:12:00Z"));

        when(conversationRepository.findAllByMemberId(userId)).thenReturn(List.of(conversation));
        when(conversationUserSettingRepository.findByUserIdAndConversationIdIn(any(), any()))
                .thenReturn(List.of(setting));
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(any(), any())).thenReturn(0L);

        ConversationResponse response = conversationService.getMyConversations(userId, true).get(0);

        assertThat(response.isMuted()).isTrue();
        assertThat(response.isArchived()).isTrue();
        assertThat(response.isPinned()).isTrue();
    }

    @Test
    void archivedConversationListReturnsOnlyArchivedConversationsAndKeepsPinnedFirst() {
        UUID userId = UUID.randomUUID();
        UUID archivedPinnedId = UUID.randomUUID();
        UUID archivedNormalId = UUID.randomUUID();
        UUID activeId = UUID.randomUUID();

        Conversation archivedPinned = new Conversation();
        archivedPinned.setId(archivedPinnedId);
        archivedPinned.setCreatorId(userId);
        archivedPinned.setType(ConversationType.PRIVATE);
        archivedPinned.setCreatedAt(java.time.Instant.parse("2026-04-01T08:00:00Z"));

        Conversation archivedNormal = groupConversation(archivedNormalId, userId);
        archivedNormal.setCreatedAt(java.time.Instant.parse("2026-04-01T09:00:00Z"));

        Conversation active = groupConversation(activeId, userId);
        active.setCreatedAt(java.time.Instant.parse("2026-04-01T10:00:00Z"));

        ConversationUserSetting archivedPinnedSetting = new ConversationUserSetting();
        archivedPinnedSetting.setConversationId(archivedPinnedId);
        archivedPinnedSetting.setUserId(userId);
        archivedPinnedSetting.setArchivedAt(java.time.Instant.parse("2026-04-01T12:00:00Z"));
        archivedPinnedSetting.setPinnedAt(java.time.Instant.parse("2026-04-01T12:01:00Z"));

        ConversationUserSetting archivedNormalSetting = new ConversationUserSetting();
        archivedNormalSetting.setConversationId(archivedNormalId);
        archivedNormalSetting.setUserId(userId);
        archivedNormalSetting.setArchivedAt(java.time.Instant.parse("2026-04-01T12:02:00Z"));

        when(conversationRepository.findAllByMemberId(userId))
                .thenReturn(List.of(active, archivedNormal, archivedPinned));
        when(conversationUserSettingRepository.findByUserIdAndConversationIdIn(any(), any()))
                .thenReturn(List.of(archivedPinnedSetting, archivedNormalSetting));
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(any(), any())).thenReturn(0L);

        List<ConversationResponse> responses = conversationService.getMyConversations(userId, true);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo(archivedPinnedId);
        assertThat(responses).extracting(ConversationResponse::getId)
                .containsExactly(archivedPinnedId, archivedNormalId);
        assertThat(responses).allMatch(ConversationResponse::isArchived);
    }

    @Test
    void closeConversationRejectsPrivateConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(ownerId);
        conversation.setType(ConversationType.PRIVATE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));

        assertThatThrownBy(() -> conversationService.closeConversation(conversationId, ownerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only group conversations");
    }

    @Test
    void closeConversationRequiresOwner() {
        UUID conversationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, adminId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, adminId))
                .thenReturn(Optional.of(member(conversationId, adminId, MemberRole.ADMIN)));

        assertThatThrownBy(() -> conversationService.closeConversation(conversationId, adminId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void closeConversationRejectsAlreadyDeletedConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);
        conversation.setDeletedAt(java.time.Instant.now());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> conversationService.closeConversation(conversationId, ownerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been deleted");
    }

    @Test
    void renameConversationRejectsDeletedConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);
        conversation.setDeletedAt(java.time.Instant.now());

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> conversationService.renameConversation(conversationId, ownerId, "Renamed"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void roleManagementRejectsInvalidTransitions() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation conversation = groupConversation(conversationId, ownerId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(member(conversationId, ownerId, MemberRole.OWNER)));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, targetId))
                .thenReturn(Optional.of(member(conversationId, targetId, MemberRole.ADMIN)));
        when(userProfileRepository.findById(targetId)).thenReturn(Optional.of(activeUser(targetId)));

        assertThatThrownBy(() -> conversationService.promoteToAdmin(conversationId, ownerId, targetId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only members can be promoted");
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
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());

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
        return activeUser(userId, "User " + userId.toString().substring(0, 8), null);
    }

    private UserProfile activeUser(UUID userId, String displayName, String avatarUrl) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);
        userProfile.setDisplayName(displayName);
        userProfile.setUsername(displayName);
        userProfile.setAvatarUrl(avatarUrl);
        return userProfile;
    }
}
