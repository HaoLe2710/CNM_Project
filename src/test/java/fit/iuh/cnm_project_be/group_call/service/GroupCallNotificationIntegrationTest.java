package fit.iuh.cnm_project_be.group_call.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.group_call.dto.InitiateGroupCallRequest;
import fit.iuh.cnm_project_be.group_call.entity.GroupCall;
import fit.iuh.cnm_project_be.group_call.enums.GroupCallType;
import fit.iuh.cnm_project_be.group_call.enums.GroupCallStatus;
import fit.iuh.cnm_project_be.group_call.enums.ParticipantState;
import fit.iuh.cnm_project_be.group_call.entity.GroupCallParticipant;
import fit.iuh.cnm_project_be.group_call.repository.GroupCallParticipantRepository;
import fit.iuh.cnm_project_be.group_call.repository.GroupCallRepository;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupCallNotificationIntegrationTest {

    @Mock
    private GroupCallRepository groupCallRepository;
    @Mock
    private GroupCallParticipantRepository participantRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageStatusRepository messageStatusRepository;
    @Mock
    private MessageUserStateRepository messageUserStateRepository;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    private GroupCallService groupCallService;

    @BeforeEach
    void setUp() {
        groupCallService = new GroupCallService(
                groupCallRepository,
                participantRepository,
                conversationMemberRepository,
                userProfileRepository,
                messagingTemplate,
                messageRepository,
                messageStatusRepository,
                messageUserStateRepository,
                new ObjectMapper(),
                notificationDispatcher
        );
        when(notificationDispatcher.dispatch(any())).thenReturn(dispatchResult());
    }

    @Test
    void groupCallStartedDispatchesToMembersExceptInitiator() {
        UUID conversationId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID invitedId = UUID.randomUUID();
        mockGroupCall(conversationId, initiatorId, invitedId);

        groupCallService.initiateGroupCall(initiatorId, new InitiateGroupCallRequest(conversationId, GroupCallType.VIDEO));

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.GROUP_CALL_STARTED);
        assertThat(captor.getValue().getExplicitRecipientIds()).containsExactly(invitedId);
    }

    @Test
    void dispatchFailureDoesNotFailGroupCallCreation() {
        UUID conversationId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID invitedId = UUID.randomUUID();
        mockGroupCall(conversationId, initiatorId, invitedId);
        when(notificationDispatcher.dispatch(any())).thenThrow(new RuntimeException("down"));

        groupCallService.initiateGroupCall(initiatorId, new InitiateGroupCallRequest(conversationId, GroupCallType.VOICE));
    }

    @Test
    void cleanupRingingTimedOutGroupCallDispatchesMissedGroupCallToInvitedMembers() {
        UUID conversationId = UUID.randomUUID();
        UUID groupCallId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID invitedId = UUID.randomUUID();
        GroupCall groupCall = new GroupCall();
        groupCall.setId(groupCallId);
        groupCall.setConversationId(conversationId);
        groupCall.setInitiatorId(initiatorId);
        groupCall.setStatus(GroupCallStatus.RINGING);
        groupCall.setType(GroupCallType.VIDEO);
        groupCall.setCreatedAt(Instant.now().minusSeconds(180));
        when(groupCallRepository.findAll()).thenReturn(List.of(groupCall));
        when(groupCallRepository.findById(groupCallId)).thenReturn(Optional.of(groupCall));
        when(participantRepository.findByGroupCallIdAndState(groupCallId, ParticipantState.JOINED)).thenReturn(List.of());
        when(participantRepository.findByGroupCallId(groupCallId)).thenReturn(List.of(invitedParticipant(groupCall, invitedId)));
        UserProfile initiator = new UserProfile();
        initiator.setUserId(initiatorId);
        initiator.setDisplayName("Host");
        when(userProfileRepository.findById(initiatorId)).thenReturn(Optional.of(initiator));

        groupCallService.cleanUpStaleGroupCalls();

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MISSED_GROUP_CALL);
        assertThat(captor.getValue().getExplicitRecipientIds()).containsExactly(invitedId);
    }

    private void mockGroupCall(UUID conversationId, UUID initiatorId, UUID invitedId) {
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, initiatorId), member(conversationId, invitedId)));
        UserProfile initiator = new UserProfile();
        initiator.setUserId(initiatorId);
        initiator.setDisplayName("Host");
        when(userProfileRepository.findById(initiatorId)).thenReturn(Optional.of(initiator));
        when(groupCallRepository.save(any(GroupCall.class))).thenAnswer(invocation -> {
            GroupCall call = invocation.getArgument(0);
            call.setId(UUID.randomUUID());
            return call;
        });
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(200L);
            message.setCreatedAt(Instant.now());
            return message;
        });
    }

    private ConversationMember member(UUID conversationId, UUID userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER);
        return member;
    }

    private GroupCallParticipant invitedParticipant(GroupCall groupCall, UUID userId) {
        GroupCallParticipant participant = new GroupCallParticipant();
        participant.setGroupCall(groupCall);
        participant.setUserId(userId);
        participant.setState(ParticipantState.INVITED);
        participant.setJoinCount(0);
        return participant;
    }

    private NotificationDispatchResult dispatchResult() {
        return NotificationDispatchResult.builder()
                .candidateRecipientCount(1)
                .policyAllowedInAppCount(1)
                .policyAllowedPushCount(1)
                .createdNotificationCount(1)
                .pushSuccessCount(1)
                .pushFailureCount(0)
                .deniedRecipients(Map.of())
                .createdNotificationIds(List.of(UUID.randomUUID()))
                .errors(List.of())
                .build();
    }
}
