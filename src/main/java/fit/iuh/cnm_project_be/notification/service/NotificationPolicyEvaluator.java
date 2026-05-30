package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.NotificationPolicyContext;
import fit.iuh.cnm_project_be.notification.dto.NotificationPolicyResult;
import fit.iuh.cnm_project_be.notification.dto.UserNotificationPreference;
import fit.iuh.cnm_project_be.notification.enums.NotificationPolicyDenyReason;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationPolicyEvaluator {

    private final ConversationNotificationSettingService conversationNotificationSettingService;
    private final ConversationMembershipPolicyService conversationMembershipPolicyService;
    private final UserBlockPolicyService userBlockPolicyService;
    private final UserNotificationPreferencePolicyService userNotificationPreferencePolicyService;
    private final NotificationEventClassifier notificationEventClassifier;
    private final Clock notificationPolicyClock;

    @Transactional(readOnly = true)
    public NotificationPolicyResult evaluate(NotificationPolicyContext context) {
        List<NotificationPolicyDenyReason> reasons = new ArrayList<>();
        if (context == null || context.getRecipientId() == null) {
            reasons.add(NotificationPolicyDenyReason.RECIPIENT_NOT_FOUND);
            return result(false, false, false, reasons);
        }
        if (context.getNotificationType() == null) {
            reasons.add(NotificationPolicyDenyReason.UNKNOWN);
            return result(false, false, true, reasons);
        }

        if (context.getActorId() != null && context.getActorId().equals(context.getRecipientId())) {
            reasons.add(NotificationPolicyDenyReason.SELF_NOTIFICATION);
        }

        if (userBlockPolicyService.hasRecipientBlockedActor(context.getRecipientId(), context.getActorId())) {
            reasons.add(NotificationPolicyDenyReason.RECIPIENT_BLOCKED_ACTOR);
        }
        if (userBlockPolicyService.hasActorBlockedRecipient(context.getActorId(), context.getRecipientId())) {
            reasons.add(NotificationPolicyDenyReason.ACTOR_BLOCKED_RECIPIENT);
        }

        if (context.getConversationId() != null) {
            evaluateConversationPolicy(context, reasons);
        }

        UserNotificationPreference preferences = userNotificationPreferencePolicyService.getPreferences(context.getRecipientId());
        evaluateGlobalPreferences(context.getNotificationType(), preferences, reasons);

        boolean hardDenied = reasons.stream().anyMatch(this::isHardDeny);
        boolean pushDenied = hardDenied || reasons.contains(NotificationPolicyDenyReason.PUSH_DISABLED);
        boolean previewAllowed = !reasons.contains(NotificationPolicyDenyReason.PREVIEW_DISABLED);

        return result(!hardDenied, !pushDenied, previewAllowed, reasons);
    }

    private void evaluateConversationPolicy(
            NotificationPolicyContext context,
            List<NotificationPolicyDenyReason> reasons) {
        if (!conversationMembershipPolicyService.isActiveMember(context.getConversationId(), context.getRecipientId())) {
            reasons.add(NotificationPolicyDenyReason.NOT_CONVERSATION_MEMBER);
            return;
        }

        ConversationUserSetting setting = conversationNotificationSettingService
                .findSettingForPolicy(context.getConversationId(), context.getRecipientId())
                .orElse(null);
        ConversationNotificationLevel level = conversationNotificationSettingService.resolveNotificationLevel(setting);
        Instant now = Instant.now(notificationPolicyClock);

        if (level == ConversationNotificationLevel.NONE) {
            reasons.add(NotificationPolicyDenyReason.NOTIFICATION_LEVEL_NONE);
            return;
        }
        if (conversationNotificationSettingService.isMuted(setting, now)) {
            reasons.add(NotificationPolicyDenyReason.CONVERSATION_MUTED);
            return;
        }
        if (level == ConversationNotificationLevel.MENTIONS_ONLY && !qualifiesForMentionsOnly(context)) {
            reasons.add(NotificationPolicyDenyReason.MENTIONS_ONLY_NOT_MATCHED);
        }
    }

    private boolean qualifiesForMentionsOnly(NotificationPolicyContext context) {
        NotificationType type = context.getNotificationType();
        if (type == NotificationType.GROUP_MENTION
                || type == NotificationType.REPLY_TO_MY_MESSAGE
                || type == NotificationType.COMMENT_MENTION
                || context.isDirectMention()
                || context.isReplyToRecipientMessage()
                || context.isRecipientDirectlyAffected()) {
            return true;
        }
        return type == NotificationType.NEW_PRIVATE_MESSAGE
                && conversationMembershipPolicyService.isPrivateConversation(context.getConversationId());
    }

    private void evaluateGlobalPreferences(
            NotificationType type,
            UserNotificationPreference preferences,
            List<NotificationPolicyDenyReason> reasons) {
        if (!preferences.isNotificationsEnabled()) {
            reasons.add(NotificationPolicyDenyReason.GLOBAL_NOTIFICATIONS_DISABLED);
        }
        if (!preferences.isPushEnabled()) {
            reasons.add(NotificationPolicyDenyReason.PUSH_DISABLED);
        }
        if (!preferences.isPreviewEnabled()) {
            reasons.add(NotificationPolicyDenyReason.PREVIEW_DISABLED);
        }
        if (!preferences.isChatNotificationsEnabled()
                && (notificationEventClassifier.isChatEvent(type) || notificationEventClassifier.isGroupLifecycleEvent(type))) {
            reasons.add(NotificationPolicyDenyReason.GLOBAL_CHAT_NOTIFICATIONS_DISABLED);
        }
        if (!preferences.isCallNotificationsEnabled() && notificationEventClassifier.isCallEvent(type)) {
            reasons.add(NotificationPolicyDenyReason.GLOBAL_CALL_NOTIFICATIONS_DISABLED);
        }
        if (!preferences.isSocialNotificationsEnabled() && notificationEventClassifier.isSocialEvent(type)) {
            reasons.add(NotificationPolicyDenyReason.GLOBAL_SOCIAL_NOTIFICATIONS_DISABLED);
        }
    }

    private boolean isHardDeny(NotificationPolicyDenyReason reason) {
        return reason != NotificationPolicyDenyReason.PUSH_DISABLED
                && reason != NotificationPolicyDenyReason.PREVIEW_DISABLED;
    }

    private NotificationPolicyResult result(
            boolean inAppAllowed,
            boolean pushAllowed,
            boolean previewAllowed,
            List<NotificationPolicyDenyReason> reasons) {
        return NotificationPolicyResult.builder()
                .inAppAllowed(inAppAllowed)
                .pushAllowed(pushAllowed)
                .previewAllowed(previewAllowed)
                .denyReasons(List.copyOf(reasons))
                .build();
    }
}
