package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.CreateNotificationCommand;
import fit.iuh.cnm_project_be.notification.dto.NotificationContent;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
import fit.iuh.cnm_project_be.notification.dto.NotificationPolicyContext;
import fit.iuh.cnm_project_be.notification.dto.NotificationPolicyResult;
import fit.iuh.cnm_project_be.notification.dto.NotificationResponse;
import fit.iuh.cnm_project_be.notification.dto.PushNotificationCommand;
import fit.iuh.cnm_project_be.notification.dto.PushSendResult;
import fit.iuh.cnm_project_be.notification.enums.NotificationPolicyDenyReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final NotificationPolicyEvaluator notificationPolicyEvaluator;
    private final InAppNotificationService inAppNotificationService;
    private final PushNotificationService pushNotificationService;
    private final NotificationContentBuilder contentBuilder;

    public NotificationDispatchResult dispatch(NotificationDispatchRequest request) {
        return dispatchToRecipients(request, request != null ? request.getExplicitRecipientIds() : null);
    }

    public NotificationDispatchResult dispatchToRecipients(NotificationDispatchRequest request, Collection<UUID> recipientIds) {
        if (request == null || request.getType() == null || recipientIds == null || recipientIds.isEmpty()) {
            return emptyResult();
        }

        List<UUID> recipients = recipientIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        int candidateRecipientCount = recipients.size();
        int policyAllowedInAppCount = 0;
        int policyAllowedPushCount = 0;
        int createdNotificationCount = 0;
        int pushSuccessCount = 0;
        int pushFailureCount = 0;
        Map<UUID, List<NotificationPolicyDenyReason>> deniedRecipients = new LinkedHashMap<>();
        List<UUID> createdNotificationIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (UUID recipientId : recipients) {
            if (request.getActorId() != null && request.getActorId().equals(recipientId)) {
                deniedRecipients.put(recipientId, List.of(NotificationPolicyDenyReason.SELF_NOTIFICATION));
                continue;
            }

            NotificationPolicyContext context = buildPolicyContext(request, recipientId);
            NotificationPolicyResult policy = notificationPolicyEvaluator.evaluate(context);
            if (!policy.isInAppAllowed() && !policy.isPushAllowed()) {
                deniedRecipients.put(recipientId, policy.getDenyReasons());
                log.debug("[NotificationDispatcher] denied recipient={} type={} reasons={}",
                        recipientId,
                        request.getType(),
                        policy.getDenyReasons());
                continue;
            }

            NotificationContent content = contentBuilder.build(request, policy.isPreviewAllowed());
            UUID notificationId = null;
            if (policy.isInAppAllowed() && !request.isForceNoInApp()) {
                policyAllowedInAppCount++;
                try {
                    NotificationResponse notification = inAppNotificationService.createAndPublish(CreateNotificationCommand.builder()
                            .recipientId(recipientId)
                            .actorId(request.getActorId())
                            .type(request.getType())
                            .title(content.getTitle())
                            .body(content.getBody())
                            .targetType(request.getTargetType())
                            .targetId(request.getTargetId())
                            .conversationId(request.getConversationId())
                            .messageId(request.getMessageId())
                            .postId(request.getPostId())
                            .commentId(request.getCommentId())
                            .metadata(content.getMetadata())
                            .dedupKey(buildDedupKey(request, recipientId))
                            .build());
                    notificationId = notification.getId();
                    createdNotificationIds.add(notification.getId());
                    createdNotificationCount++;
                } catch (Exception ex) {
                    errors.add("inApp recipient=" + recipientId + " error=" + ex.getMessage());
                    log.warn("[NotificationDispatcher] in-app notification failed recipient={} type={}: {}",
                            recipientId,
                            request.getType(),
                            ex.getMessage());
                }
            }

            if (policy.isPushAllowed() && !request.isForceNoPush()) {
                policyAllowedPushCount++;
                try {
                    PushSendResult pushResult = pushNotificationService.sendToUser(recipientId, PushNotificationCommand.builder()
                            .title(content.getTitle())
                            .body(content.getBody())
                            .data(content.getPushData())
                            .notificationId(notificationId)
                            .notificationType(request.getType())
                            .targetType(request.getTargetType())
                            .targetId(request.getTargetId())
                            .conversationId(request.getConversationId())
                            .messageId(request.getMessageId())
                            .postId(request.getPostId())
                            .commentId(request.getCommentId())
                            .actorId(request.getActorId())
                            .build());
                    pushSuccessCount += pushResult.getSuccessCount();
                    pushFailureCount += pushResult.getFailureCount();
                } catch (Exception ex) {
                    pushFailureCount++;
                    errors.add("push recipient=" + recipientId + " error=" + ex.getMessage());
                    log.warn("[NotificationDispatcher] push notification failed recipient={} type={}: {}",
                            recipientId,
                            request.getType(),
                            ex.getMessage());
                }
            }
        }

        return NotificationDispatchResult.builder()
                .candidateRecipientCount(candidateRecipientCount)
                .policyAllowedInAppCount(policyAllowedInAppCount)
                .policyAllowedPushCount(policyAllowedPushCount)
                .createdNotificationCount(createdNotificationCount)
                .pushSuccessCount(pushSuccessCount)
                .pushFailureCount(pushFailureCount)
                .deniedRecipients(deniedRecipients)
                .createdNotificationIds(createdNotificationIds)
                .errors(errors)
                .build();
    }

    private NotificationPolicyContext buildPolicyContext(NotificationDispatchRequest request, UUID recipientId) {
        return NotificationPolicyContext.builder()
                .notificationType(request.getType())
                .targetType(request.getTargetType())
                .actorId(request.getActorId())
                .recipientId(recipientId)
                .conversationId(request.getConversationId())
                .messageId(request.getMessageId())
                .postId(request.getPostId())
                .commentId(request.getCommentId())
                .directMention(request.isDirectMention())
                .replyToRecipientMessage(request.isReplyToRecipientMessage())
                .recipientDirectlyAffected(request.isRecipientDirectlyAffected())
                .metadata(request.getMetadata())
                .build();
    }

    private String buildDedupKey(NotificationDispatchRequest request, UUID recipientId) {
        String prefix = request.getDedupKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = request.getType().name().toLowerCase() + ":" + request.getTargetId();
        }
        return prefix + ":recipient:" + recipientId;
    }

    private NotificationDispatchResult emptyResult() {
        return NotificationDispatchResult.builder()
                .candidateRecipientCount(0)
                .policyAllowedInAppCount(0)
                .policyAllowedPushCount(0)
                .createdNotificationCount(0)
                .pushSuccessCount(0)
                .pushFailureCount(0)
                .deniedRecipients(Map.of())
                .createdNotificationIds(List.of())
                .errors(List.of())
                .build();
    }
}
