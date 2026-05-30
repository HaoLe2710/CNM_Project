package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.NotificationPolicyContext;
import fit.iuh.cnm_project_be.notification.dto.NotificationPolicyResult;
import fit.iuh.cnm_project_be.notification.dto.UserNotificationPreference;
import fit.iuh.cnm_project_be.notification.enums.NotificationPolicyDenyReason;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPolicyEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-05-28T10:30:00Z");

    @Mock
    private ConversationNotificationSettingService settingService;
    @Mock
    private ConversationMembershipPolicyService membershipPolicyService;
    @Mock
    private UserBlockPolicyService userBlockPolicyService;
    @Mock
    private UserNotificationPreferencePolicyService preferencePolicyService;

    private NotificationPolicyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new NotificationPolicyEvaluator(
                settingService,
                membershipPolicyService,
                userBlockPolicyService,
                preferencePolicyService,
                new NotificationEventClassifier(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(preferencePolicyService.getPreferences(any())).thenReturn(defaultPreferences());
        lenient().when(settingService.resolveNotificationLevel(nullable(ConversationUserSetting.class)))
                .thenCallRealMethod();
        lenient().when(settingService.isMuted(nullable(ConversationUserSetting.class), any()))
                .thenCallRealMethod();
    }

    @Test
    void selfNotificationDenied() {
        UUID userId = UUID.randomUUID();

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .actorId(userId)
                .recipientId(userId)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.SELF_NOTIFICATION);
    }

    @Test
    void noConversationAllowsByDefault() {
        NotificationPolicyResult result = evaluator.evaluate(baseContext().conversationId(null).build());

        assertThat(result.isInAppAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
    }

    @Test
    void conversationMemberAllLevelAllows() {
        UUID conversationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        mockMember(conversationId, recipientId, setting(conversationId, recipientId, ConversationNotificationLevel.ALL));

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .conversationId(conversationId)
                .recipientId(recipientId)
                .build());

        assertThat(result.isInAppAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
    }

    @Test
    void nonMemberDenied() {
        UUID conversationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(membershipPolicyService.isActiveMember(conversationId, recipientId)).thenReturn(false);

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .conversationId(conversationId)
                .recipientId(recipientId)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.NOT_CONVERSATION_MEMBER);
    }

    @Test
    void notificationLevelNoneDeniesInAppAndPush() {
        UUID conversationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        mockMember(conversationId, recipientId, setting(conversationId, recipientId, ConversationNotificationLevel.NONE));

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .conversationId(conversationId)
                .recipientId(recipientId)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.NOTIFICATION_LEVEL_NONE);
    }

    @Test
    void mutedUntilFutureDeniesInAppAndPush() {
        UUID conversationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        ConversationUserSetting setting = setting(conversationId, recipientId, ConversationNotificationLevel.ALL);
        setting.setMutedUntil(NOW.plusSeconds(60));
        mockMember(conversationId, recipientId, setting);

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .conversationId(conversationId)
                .recipientId(recipientId)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.CONVERSATION_MUTED);
    }

    @Test
    void mutedUntilPastAllows() {
        UUID conversationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        ConversationUserSetting setting = setting(conversationId, recipientId, ConversationNotificationLevel.ALL);
        setting.setMutedUntil(NOW.minusSeconds(60));
        mockMember(conversationId, recipientId, setting);

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .conversationId(conversationId)
                .recipientId(recipientId)
                .build());

        assertThat(result.isInAppAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
    }

    @Test
    void mentionsOnlyGroupMessageDenied() {
        UUID conversationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        mockMember(conversationId, recipientId, setting(conversationId, recipientId, ConversationNotificationLevel.MENTIONS_ONLY));

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .notificationType(NotificationType.NEW_GROUP_MESSAGE)
                .conversationId(conversationId)
                .recipientId(recipientId)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.MENTIONS_ONLY_NOT_MATCHED);
    }

    @Test
    void mentionsOnlyGroupMentionAllowed() {
        assertMentionsOnlyAllowed(baseContext().notificationType(NotificationType.GROUP_MENTION));
    }

    @Test
    void mentionsOnlyReplyToMyMessageAllowed() {
        assertMentionsOnlyAllowed(baseContext().notificationType(NotificationType.REPLY_TO_MY_MESSAGE));
    }

    @Test
    void mentionsOnlyDirectlyAffectedAllowed() {
        assertMentionsOnlyAllowed(baseContext()
                .notificationType(NotificationType.GROUP_MEMBER_REMOVED)
                .recipientDirectlyAffected(true));
    }

    @Test
    void privateMessageMentionsOnlyAllowedIfPrivateConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        mockMember(conversationId, recipientId, setting(conversationId, recipientId, ConversationNotificationLevel.MENTIONS_ONLY));
        when(membershipPolicyService.isPrivateConversation(conversationId)).thenReturn(true);

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .notificationType(NotificationType.NEW_PRIVATE_MESSAGE)
                .conversationId(conversationId)
                .recipientId(recipientId)
                .build());

        assertThat(result.isInAppAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
    }

    @Test
    void recipientBlockedActorDenied() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(userBlockPolicyService.hasRecipientBlockedActor(recipientId, actorId)).thenReturn(true);

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .actorId(actorId)
                .recipientId(recipientId)
                .conversationId(null)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.RECIPIENT_BLOCKED_ACTOR);
    }

    @Test
    void actorBlockedRecipientDenied() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(userBlockPolicyService.hasActorBlockedRecipient(actorId, recipientId)).thenReturn(true);

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .actorId(actorId)
                .recipientId(recipientId)
                .conversationId(null)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.ACTOR_BLOCKED_RECIPIENT);
    }

    @Test
    void globalNotificationsOffDenied() {
        UUID recipientId = UUID.randomUUID();
        when(preferencePolicyService.getPreferences(recipientId)).thenReturn(defaultPreferences().toBuilder()
                .notificationsEnabled(false)
                .build());

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .recipientId(recipientId)
                .conversationId(null)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.GLOBAL_NOTIFICATIONS_DISABLED);
    }

    @Test
    void chatNotificationsOffDeniesChat() {
        UUID recipientId = UUID.randomUUID();
        when(preferencePolicyService.getPreferences(recipientId)).thenReturn(defaultPreferences().toBuilder()
                .chatNotificationsEnabled(false)
                .build());

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .notificationType(NotificationType.NEW_PRIVATE_MESSAGE)
                .recipientId(recipientId)
                .conversationId(null)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.GLOBAL_CHAT_NOTIFICATIONS_DISABLED);
    }

    @Test
    void callNotificationsOffDeniesCall() {
        UUID recipientId = UUID.randomUUID();
        when(preferencePolicyService.getPreferences(recipientId)).thenReturn(defaultPreferences().toBuilder()
                .callNotificationsEnabled(false)
                .build());

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .notificationType(NotificationType.INCOMING_PRIVATE_CALL)
                .recipientId(recipientId)
                .conversationId(null)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.GLOBAL_CALL_NOTIFICATIONS_DISABLED);
    }

    @Test
    void socialNotificationsOffDeniesSocial() {
        UUID recipientId = UUID.randomUUID();
        when(preferencePolicyService.getPreferences(recipientId)).thenReturn(defaultPreferences().toBuilder()
                .socialNotificationsEnabled(false)
                .build());

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .notificationType(NotificationType.POST_COMMENT)
                .recipientId(recipientId)
                .conversationId(null)
                .build());

        assertDenied(result, NotificationPolicyDenyReason.GLOBAL_SOCIAL_NOTIFICATIONS_DISABLED);
    }

    @Test
    void pushDisabledDeniesPushButAllowsInApp() {
        UUID recipientId = UUID.randomUUID();
        when(preferencePolicyService.getPreferences(recipientId)).thenReturn(defaultPreferences().toBuilder()
                .pushEnabled(false)
                .build());

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .recipientId(recipientId)
                .conversationId(null)
                .build());

        assertThat(result.isInAppAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isFalse();
        assertThat(result.getDenyReasons()).contains(NotificationPolicyDenyReason.PUSH_DISABLED);
    }

    @Test
    void previewDisabledSetsPreviewAllowedFalse() {
        UUID recipientId = UUID.randomUUID();
        when(preferencePolicyService.getPreferences(recipientId)).thenReturn(defaultPreferences().toBuilder()
                .previewEnabled(false)
                .build());

        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .recipientId(recipientId)
                .conversationId(null)
                .build());

        assertThat(result.isInAppAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
        assertThat(result.isPreviewAllowed()).isFalse();
        assertThat(result.getDenyReasons()).contains(NotificationPolicyDenyReason.PREVIEW_DISABLED);
    }

    @Test
    void nullActorSystemNoticeAllowedIfRecipientValid() {
        NotificationPolicyResult result = evaluator.evaluate(baseContext()
                .actorId(null)
                .notificationType(NotificationType.SYSTEM_NOTICE)
                .conversationId(null)
                .build());

        assertThat(result.isInAppAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
    }

    private void assertMentionsOnlyAllowed(NotificationPolicyContext.NotificationPolicyContextBuilder builder) {
        UUID conversationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        mockMember(conversationId, recipientId, setting(conversationId, recipientId, ConversationNotificationLevel.MENTIONS_ONLY));

        NotificationPolicyResult result = evaluator.evaluate(builder
                .conversationId(conversationId)
                .recipientId(recipientId)
                .build());

        assertThat(result.isInAppAllowed()).isTrue();
        assertThat(result.isPushAllowed()).isTrue();
    }

    private NotificationPolicyContext.NotificationPolicyContextBuilder baseContext() {
        return NotificationPolicyContext.builder()
                .notificationType(NotificationType.NEW_GROUP_MESSAGE)
                .actorId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .conversationId(UUID.randomUUID());
    }

    private void mockMember(UUID conversationId, UUID recipientId, ConversationUserSetting setting) {
        when(membershipPolicyService.isActiveMember(conversationId, recipientId)).thenReturn(true);
        lenient().when(settingService.findSettingForPolicy(conversationId, recipientId)).thenReturn(Optional.ofNullable(setting));
    }

    private ConversationUserSetting setting(
            UUID conversationId,
            UUID userId,
            ConversationNotificationLevel level) {
        ConversationUserSetting setting = new ConversationUserSetting();
        setting.setConversationId(conversationId);
        setting.setUserId(userId);
        setting.setNotificationLevel(level);
        return setting;
    }

    private UserNotificationPreference defaultPreferences() {
        return UserNotificationPreference.builder()
                .notificationsEnabled(true)
                .chatNotificationsEnabled(true)
                .callNotificationsEnabled(true)
                .socialNotificationsEnabled(true)
                .pushEnabled(true)
                .previewEnabled(true)
                .build();
    }

    private void assertDenied(NotificationPolicyResult result, NotificationPolicyDenyReason reason) {
        assertThat(result.isInAppAllowed()).isFalse();
        assertThat(result.isPushAllowed()).isFalse();
        assertThat(result.getDenyReasons()).contains(reason);
    }
}
