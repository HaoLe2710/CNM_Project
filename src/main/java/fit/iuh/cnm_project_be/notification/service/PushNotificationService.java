package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.PushNotificationCommand;
import fit.iuh.cnm_project_be.notification.dto.PushSendResult;
import fit.iuh.cnm_project_be.notification.dto.SinglePushSendResult;
import fit.iuh.cnm_project_be.notification.entity.DeviceToken;
import fit.iuh.cnm_project_be.notification.enums.PushProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PushNotificationService {

    private final DeviceTokenService deviceTokenService;
    private final Map<PushProvider, PushSender> sendersByProvider;

    public PushNotificationService(DeviceTokenService deviceTokenService, List<PushSender> pushSenders) {
        this.deviceTokenService = deviceTokenService;
        this.sendersByProvider = pushSenders.stream()
                .collect(Collectors.toMap(PushSender::provider, Function.identity()));
    }

    public PushSendResult sendToUser(UUID userId, PushNotificationCommand command) {
        if (userId == null) {
            return emptyResult(0);
        }
        return sendToTokens(List.of(userId), deviceTokenService.findActiveTokensByUserId(userId), command);
    }

    public PushSendResult sendToUsers(Collection<UUID> userIds, PushNotificationCommand command) {
        if (userIds == null || userIds.isEmpty()) {
            return emptyResult(0);
        }
        List<UUID> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return sendToTokens(distinctUserIds, deviceTokenService.findActiveTokensByUserIds(distinctUserIds), command);
    }

    public PushSendResult sendToTokens(Collection<UUID> requestedUserIds, Collection<DeviceToken> tokens, PushNotificationCommand command) {
        int requestedUserCount = requestedUserIds == null ? 0 : requestedUserIds.size();
        if (tokens == null || tokens.isEmpty()) {
            return emptyResult(requestedUserCount);
        }

        int successCount = 0;
        int failureCount = 0;
        int disabledTokenCount = 0;
        List<UUID> failedTokenIds = new ArrayList<>();
        List<String> providerErrors = new ArrayList<>();

        for (DeviceToken token : tokens) {
            PushSender sender = sendersByProvider.get(token.getProvider());
            if (sender == null) {
                failureCount++;
                failedTokenIds.add(token.getId());
                providerErrors.add("No sender for provider " + token.getProvider());
                continue;
            }

            SinglePushSendResult result = sender.send(token, command);
            if (result.success()) {
                successCount++;
                continue;
            }

            failureCount++;
            failedTokenIds.add(token.getId());
            providerErrors.add(formatProviderError(token, result));
            if (result.invalidToken()) {
                disabledTokenCount++;
                deviceTokenService.disableTokenAfterFailure(token.getId(), result.providerErrorCode());
            }
        }

        return PushSendResult.builder()
                .requestedUserCount(requestedUserCount)
                .targetTokenCount(tokens.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .disabledTokenCount(disabledTokenCount)
                .failedTokenIds(failedTokenIds)
                .providerErrors(providerErrors)
                .build();
    }

    public static Map<String, String> toStringData(PushNotificationCommand command) {
        Map<String, String> data = new LinkedHashMap<>();
        if (command == null) {
            return data;
        }
        putAll(data, command.getData());
        put(data, "notificationId", command.getNotificationId());
        put(data, "notificationType", command.getNotificationType());
        put(data, "targetType", command.getTargetType());
        put(data, "targetId", command.getTargetId());
        put(data, "conversationId", command.getConversationId());
        put(data, "messageId", command.getMessageId());
        put(data, "postId", command.getPostId());
        put(data, "commentId", command.getCommentId());
        put(data, "actorId", command.getActorId());
        return data;
    }

    private static void putAll(Map<String, String> target, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((key, value) -> put(target, key, value));
    }

    private static void put(Map<String, String> target, String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        target.put(key, String.valueOf(value));
    }

    private PushSendResult emptyResult(int requestedUserCount) {
        return PushSendResult.builder()
                .requestedUserCount(requestedUserCount)
                .targetTokenCount(0)
                .successCount(0)
                .failureCount(0)
                .disabledTokenCount(0)
                .failedTokenIds(List.of())
                .providerErrors(List.of())
                .build();
    }

    private String formatProviderError(DeviceToken token, SinglePushSendResult result) {
        return "tokenId=" + token.getId()
                + " provider=" + token.getProvider()
                + " code=" + result.providerErrorCode()
                + " message=" + result.providerErrorMessage();
    }
}
