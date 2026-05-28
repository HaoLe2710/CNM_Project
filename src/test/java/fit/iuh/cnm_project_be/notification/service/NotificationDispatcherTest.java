package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
import fit.iuh.cnm_project_be.notification.dto.NotificationPolicyContext;
import fit.iuh.cnm_project_be.notification.dto.NotificationPolicyResult;
import fit.iuh.cnm_project_be.notification.dto.NotificationResponse;
import fit.iuh.cnm_project_be.notification.dto.PushSendResult;
import fit.iuh.cnm_project_be.notification.enums.NotificationPolicyDenyReason;
import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationPolicyEvaluator policyEvaluator;
    @Mock
    private InAppNotificationService inAppNotificationService;
    @Mock
    private PushNotificationService pushNotificationService;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(
                policyEvaluator,
                inAppNotificationService,
                pushNotificationService,
                new NotificationContentBuilder()
        );
    }

    @Test
    void dispatchPolicyAllowsCreatesInAppAndPush() {
        UUID recipientId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(policyEvaluator.evaluate(any(NotificationPolicyContext.class))).thenReturn(policy(true, true, true));
        when(inAppNotificationService.createAndPublish(any())).thenReturn(NotificationResponse.builder().id(notificationId).build());
        when(pushNotificationService.sendToUser(any(), any())).thenReturn(pushResult(1, 0));

        NotificationDispatchResult result = dispatcher.dispatch(request(List.of(recipientId)));

        assertThat(result.getCreatedNotificationCount()).isEqualTo(1);
        assertThat(result.getPushSuccessCount()).isEqualTo(1);
        assertThat(result.getCreatedNotificationIds()).containsExactly(notificationId);
    }

    @Test
    void dispatchPolicyDeniesCreatesNothingAndPushesNothing() {
        UUID recipientId = UUID.randomUUID();
        when(policyEvaluator.evaluate(any(NotificationPolicyContext.class))).thenReturn(policy(false, false, true,
                NotificationPolicyDenyReason.CONVERSATION_MUTED));

        NotificationDispatchResult result = dispatcher.dispatch(request(List.of(recipientId)));

        assertThat(result.getDeniedRecipients()).containsKey(recipientId);
        verify(inAppNotificationService, never()).createAndPublish(any());
        verify(pushNotificationService, never()).sendToUser(any(), any());
    }

    @Test
    void dispatchInAppAllowedPushDeniedCreatesOnlyInApp() {
        UUID recipientId = UUID.randomUUID();
        when(policyEvaluator.evaluate(any(NotificationPolicyContext.class))).thenReturn(policy(true, false, true,
                NotificationPolicyDenyReason.PUSH_DISABLED));
        when(inAppNotificationService.createAndPublish(any())).thenReturn(NotificationResponse.builder().id(UUID.randomUUID()).build());

        NotificationDispatchResult result = dispatcher.dispatch(request(List.of(recipientId)));

        assertThat(result.getCreatedNotificationCount()).isEqualTo(1);
        assertThat(result.getPolicyAllowedPushCount()).isZero();
        verify(pushNotificationService, never()).sendToUser(any(), any());
    }

    @Test
    void dispatchPreviewDisabledUsesGenericBody() {
        UUID recipientId = UUID.randomUUID();
        when(policyEvaluator.evaluate(any(NotificationPolicyContext.class))).thenReturn(policy(true, false, false,
                NotificationPolicyDenyReason.PREVIEW_DISABLED));
        when(inAppNotificationService.createAndPublish(any())).thenReturn(NotificationResponse.builder().id(UUID.randomUUID()).build());

        dispatcher.dispatch(request(List.of(recipientId)));

        ArgumentCaptor<fit.iuh.cnm_project_be.notification.dto.CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(fit.iuh.cnm_project_be.notification.dto.CreateNotificationCommand.class);
        verify(inAppNotificationService).createAndPublish(captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo("Bạn có tin nhắn mới");
    }

    @Test
    void dispatchPushFailureDoesNotThrow() {
        UUID recipientId = UUID.randomUUID();
        when(policyEvaluator.evaluate(any(NotificationPolicyContext.class))).thenReturn(policy(false, true, true));
        when(pushNotificationService.sendToUser(any(), any())).thenThrow(new RuntimeException("push down"));

        NotificationDispatchResult result = dispatcher.dispatch(request(List.of(recipientId)));

        assertThat(result.getPushFailureCount()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    void dispatchSelfRecipientSkipped() {
        UUID actorId = UUID.randomUUID();

        NotificationDispatchResult result = dispatcher.dispatch(request(actorId, List.of(actorId)));

        assertThat(result.getDeniedRecipients().get(actorId)).contains(NotificationPolicyDenyReason.SELF_NOTIFICATION);
        verify(policyEvaluator, never()).evaluate(any());
    }

    @Test
    void dispatchMultipleRecipientsEvaluatesEachRecipientIndependently() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(policyEvaluator.evaluate(any(NotificationPolicyContext.class)))
                .thenReturn(policy(true, false, true))
                .thenReturn(policy(false, false, true, NotificationPolicyDenyReason.NOTIFICATION_LEVEL_NONE));
        when(inAppNotificationService.createAndPublish(any())).thenReturn(NotificationResponse.builder().id(UUID.randomUUID()).build());

        NotificationDispatchResult result = dispatcher.dispatch(request(List.of(first, second)));

        assertThat(result.getCreatedNotificationCount()).isEqualTo(1);
        assertThat(result.getDeniedRecipients()).containsKey(second);
    }

    private NotificationDispatchRequest request(List<UUID> recipients) {
        return request(UUID.randomUUID(), recipients);
    }

    private NotificationDispatchRequest request(UUID actorId, List<UUID> recipients) {
        return NotificationDispatchRequest.builder()
                .type(NotificationType.NEW_PRIVATE_MESSAGE)
                .targetType(NotificationTargetType.CONVERSATION)
                .targetId(UUID.randomUUID())
                .conversationId(UUID.randomUUID())
                .messageId(10L)
                .actorId(actorId)
                .explicitRecipientIds(recipients)
                .metadata(Map.of("actorName", "An", "messagePreview", "secret"))
                .dedupKeyPrefix("message:10:type:NEW_PRIVATE_MESSAGE")
                .build();
    }

    private NotificationPolicyResult policy(boolean inApp, boolean push, boolean preview, NotificationPolicyDenyReason... reasons) {
        return NotificationPolicyResult.builder()
                .inAppAllowed(inApp)
                .pushAllowed(push)
                .previewAllowed(preview)
                .denyReasons(List.of(reasons))
                .build();
    }

    private PushSendResult pushResult(int success, int failure) {
        return PushSendResult.builder()
                .requestedUserCount(1)
                .targetTokenCount(success + failure)
                .successCount(success)
                .failureCount(failure)
                .disabledTokenCount(0)
                .failedTokenIds(List.of())
                .providerErrors(List.of())
                .build();
    }
}
