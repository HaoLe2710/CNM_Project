package fit.iuh.cnm_project_be.call.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.call.dto.InitiateCallRequest;
import fit.iuh.cnm_project_be.call.entity.Call;
import fit.iuh.cnm_project_be.call.enums.CallStatus;
import fit.iuh.cnm_project_be.call.enums.CallType;
import fit.iuh.cnm_project_be.call.repository.CallRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
import fit.iuh.cnm_project_be.notification.enums.NotificationPolicyDenyReason;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallNotificationIntegrationTest {

    @Mock
    private CallRepository callRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private FCMService fcmService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageStatusRepository messageStatusRepository;
    @Mock
    private MessageUserStateRepository messageUserStateRepository;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    private CallService callService;

    @BeforeEach
    void setUp() {
        callService = new CallService(
                callRepository,
                userProfileRepository,
                fcmService,
                messagingTemplate,
                conversationRepository,
                conversationMemberRepository,
                messageRepository,
                messageStatusRepository,
                messageUserStateRepository,
                new ObjectMapper(),
                notificationDispatcher
        );
    }

    @Test
    void incomingPrivateCallDispatchesThroughDispatcher() {
        UUID callerId = UUID.randomUUID();
        UUID calleeId = UUID.randomUUID();
        mockUsers(callerId, calleeId, null);
        when(notificationDispatcher.dispatch(any())).thenReturn(result(calleeId, 1, false));

        callService.initiateCall(callerId, new InitiateCallRequest(calleeId, CallType.VOICE));

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.INCOMING_PRIVATE_CALL);
        assertThat(captor.getValue().getExplicitRecipientIds()).containsExactly(calleeId);
        assertThat(captor.getValue().getDedupKeyPrefix()).startsWith("INCOMING_PRIVATE_CALL:");
    }

    @Test
    void devicePushSuccessPreventsLegacyFallback() {
        UUID callerId = UUID.randomUUID();
        UUID calleeId = UUID.randomUUID();
        mockUsers(callerId, calleeId, "legacy-token");
        when(notificationDispatcher.dispatch(any())).thenReturn(result(calleeId, 1, false));

        callService.initiateCall(callerId, new InitiateCallRequest(calleeId, CallType.VIDEO));

        verify(fcmService, never()).sendCallNotification(any(), any(), any(), any());
    }

    @Test
    void policyDeniedPreventsLegacyFallback() {
        UUID callerId = UUID.randomUUID();
        UUID calleeId = UUID.randomUUID();
        mockUsers(callerId, calleeId, "legacy-token");
        when(notificationDispatcher.dispatch(any())).thenReturn(result(calleeId, 0, true));

        callService.initiateCall(callerId, new InitiateCallRequest(calleeId, CallType.VOICE));

        verify(fcmService, never()).sendCallNotification(any(), any(), any(), any());
    }

    @Test
    void noDevicePushSuccessAllowsLegacyFallbackWhenPolicyAllowed() {
        UUID callerId = UUID.randomUUID();
        UUID calleeId = UUID.randomUUID();
        mockUsers(callerId, calleeId, "legacy-token");
        when(notificationDispatcher.dispatch(any())).thenReturn(result(calleeId, 0, false));

        callService.initiateCall(callerId, new InitiateCallRequest(calleeId, CallType.VOICE));

        verify(fcmService).sendCallNotification(any(), any(), any(), any());
    }

    @Test
    void dispatcherExceptionDoesNotFailCall() {
        UUID callerId = UUID.randomUUID();
        UUID calleeId = UUID.randomUUID();
        mockUsers(callerId, calleeId, "legacy-token");
        when(notificationDispatcher.dispatch(any())).thenThrow(new RuntimeException("down"));

        callService.initiateCall(callerId, new InitiateCallRequest(calleeId, CallType.VOICE));

        verify(fcmService, never()).sendCallNotification(any(), any(), any(), any());
    }

    @Test
    void callerEndsRingingCallDispatchesMissedPrivateCall() {
        UUID callerId = UUID.randomUUID();
        UUID calleeId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        Call call = new Call();
        call.setId(callId);
        call.setCallerId(callerId);
        call.setCalleeId(calleeId);
        call.setStatus(CallStatus.RINGING);
        call.setType(CallType.VOICE);
        when(callRepository.findById(callId)).thenReturn(Optional.of(call));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findPrivateConversationByParticipants(callerId, calleeId)).thenReturn(Optional.empty());
        UserProfile caller = new UserProfile();
        caller.setUserId(callerId);
        caller.setDisplayName("Caller");
        when(userProfileRepository.findById(callerId)).thenReturn(Optional.of(caller));
        when(notificationDispatcher.dispatch(any())).thenReturn(result(calleeId, 1, false));

        callService.endCall(callId, callerId);

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        assertThat(call.getStatus()).isEqualTo(CallStatus.MISSED);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MISSED_PRIVATE_CALL);
        assertThat(captor.getValue().getExplicitRecipientIds()).containsExactly(calleeId);
    }

    private void mockUsers(UUID callerId, UUID calleeId, String calleeFcmToken) {
        when(userProfileRepository.existsById(callerId)).thenReturn(true);
        when(userProfileRepository.existsById(calleeId)).thenReturn(true);
        UserProfile caller = new UserProfile();
        caller.setUserId(callerId);
        caller.setDisplayName("Caller");
        UserProfile callee = new UserProfile();
        callee.setUserId(calleeId);
        callee.setFcmToken(calleeFcmToken);
        when(userProfileRepository.findById(callerId)).thenReturn(Optional.of(caller));
        when(userProfileRepository.findById(calleeId)).thenReturn(Optional.of(callee));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> {
            Call call = invocation.getArgument(0);
            call.setId(UUID.randomUUID());
            return call;
        });
    }

    private NotificationDispatchResult result(UUID calleeId, int pushSuccess, boolean denied) {
        return NotificationDispatchResult.builder()
                .candidateRecipientCount(1)
                .policyAllowedInAppCount(denied ? 0 : 1)
                .policyAllowedPushCount(denied ? 0 : 1)
                .createdNotificationCount(denied ? 0 : 1)
                .pushSuccessCount(pushSuccess)
                .pushFailureCount(0)
                .deniedRecipients(denied ? Map.of(calleeId, List.of(NotificationPolicyDenyReason.NOTIFICATION_LEVEL_NONE)) : Map.of())
                .createdNotificationIds(List.of())
                .errors(List.of())
                .build();
    }
}
