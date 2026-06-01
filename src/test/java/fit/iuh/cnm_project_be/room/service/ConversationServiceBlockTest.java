package fit.iuh.cnm_project_be.room.service;

import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.dto.CreateConversationRequest;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceBlockTest {

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
    private UserBlockRepository userBlockRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private S3MediaStorageService s3MediaStorageService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void createPrivateConversationRejectsBlockedUsers() {
        UUID creatorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        CreateConversationRequest request = new CreateConversationRequest();
        request.setType(ConversationType.PRIVATE);
        request.setParticipantIds(List.of(otherUserId));

        when(userProfileRepository.findById(otherUserId))
                .thenReturn(Optional.of(activeUser(otherUserId)));
        when(userBlockRepository.existsByBlockerIdAndBlockedIdAndDeletedAtIsNull(creatorId, otherUserId))
                .thenReturn(true);

        assertThatThrownBy(() -> conversationService.createConversation(creatorId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("blocked");

        verifyNoInteractions(conversationRepository);
    }

    @Test
    void createPrivateConversationAllowsUsersAfterUnblock() {
        UUID creatorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        CreateConversationRequest request = new CreateConversationRequest();
        request.setType(ConversationType.PRIVATE);
        request.setParticipantIds(List.of(otherUserId));

        Conversation savedConversation = new Conversation();
        savedConversation.setId(conversationId);
        savedConversation.setCreatorId(creatorId);
        savedConversation.setType(ConversationType.PRIVATE);

        when(userProfileRepository.findById(otherUserId))
                .thenReturn(Optional.of(activeUser(otherUserId)));
        when(conversationRepository.findPrivateConversationByParticipants(creatorId, otherUserId))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(savedConversation);
        when(conversationMemberRepository.findPartnerUserId(conversationId, creatorId))
                .thenReturn(Optional.of(otherUserId));
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, creatorId))
                .thenReturn(Optional.empty());
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, otherUserId))
                .thenReturn(Optional.empty());
        when(messageRepository.findVisibleMessages(any(), any(), any())).thenReturn(List.of());
        when(messageUserStateRepository.countUnreadMessages(conversationId, creatorId)).thenReturn(0L);
        when(messageUserStateRepository.countUnreadMessages(conversationId, otherUserId)).thenReturn(0L);

        ConversationResponse response = conversationService.createConversation(creatorId, request);

        assertThat(response.getId()).isEqualTo(conversationId);
        assertThat(response.getPeerUserId()).isEqualTo(otherUserId);
    }

    private UserProfile activeUser(UUID userId) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);
        userProfile.setDisplayName("User " + userId.toString().substring(0, 8));
        userProfile.setUsername("user_" + userId.toString().substring(0, 8));
        return userProfile;
    }
}
