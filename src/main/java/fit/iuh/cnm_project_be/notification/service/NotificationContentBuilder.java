package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.NotificationContent;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class NotificationContentBuilder {

    public NotificationContent build(NotificationDispatchRequest request, boolean previewAllowed) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }

        String title = firstNonBlank(request.getTitle(), defaultTitle(request, metadata));
        String body = previewAllowed
                ? firstNonBlank(request.getBody(), defaultPreviewBody(request, metadata))
                : firstNonBlank(request.getGenericBody(), defaultGenericBody(request));

        Map<String, Object> pushData = new LinkedHashMap<>();
        pushData.put("type", request.getType() != null ? request.getType().name() : null);
        pushData.put("targetType", request.getTargetType() != null ? request.getTargetType().name() : null);
        putIfPresent(pushData, "targetId", request.getTargetId());
        putIfPresent(pushData, "conversationId", request.getConversationId());
        putIfPresent(pushData, "messageId", request.getMessageId());
        putIfPresent(pushData, "postId", request.getPostId());
        putIfPresent(pushData, "commentId", request.getCommentId());
        putIfPresent(pushData, "actorId", request.getActorId());
        copyIfPresent(metadata, pushData, "callId");
        copyIfPresent(metadata, pushData, "groupCallId");
        copyIfPresent(metadata, pushData, "roomId");
        copyIfPresent(metadata, pushData, "channel");
        copyIfPresent(metadata, pushData, "sfuUrl");

        return NotificationContent.builder()
                .title(title)
                .body(body)
                .metadata(metadata)
                .pushData(pushData)
                .build();
    }

    private String defaultTitle(NotificationDispatchRequest request, Map<String, Object> metadata) {
        String actorName = stringValue(metadata, "actorName", "Ai đó");
        String conversationName = stringValue(metadata, "conversationName", "Cuộc trò chuyện");
        NotificationType type = request.getType();
        if (type == NotificationType.NEW_PRIVATE_MESSAGE) {
            return actorName;
        }
        if (type == NotificationType.INCOMING_PRIVATE_CALL) {
            return "Cuộc gọi đến";
        }
        if (type == NotificationType.MISSED_PRIVATE_CALL) {
            return "Cuộc gọi nhỡ";
        }
        if (type == NotificationType.REACTION_TO_MY_MESSAGE || type == NotificationType.REPLY_TO_MY_MESSAGE) {
            return actorName;
        }
        if (type == NotificationType.FRIEND_REQUEST_RECEIVED || type == NotificationType.FRIEND_REQUEST_ACCEPTED) {
            return actorName;
        }
        if (type == NotificationType.POST_REACTION
                || type == NotificationType.POST_COMMENT
                || type == NotificationType.COMMENT_REPLY
                || type == NotificationType.COMMENT_MENTION
                || type == NotificationType.POST_TAGGED
                || type == NotificationType.POST_SHARED) {
            return actorName;
        }
        if (type == NotificationType.REMINDER_DUE
                || type == NotificationType.REMINDER_CREATED
                || type == NotificationType.REMINDER_UPDATED
                || type == NotificationType.REMINDER_CANCELLED) {
            return "Nhắc hẹn";
        }
        return conversationName;
    }

    private String defaultPreviewBody(NotificationDispatchRequest request, Map<String, Object> metadata) {
        String actorName = stringValue(metadata, "actorName", "Ai đó");
        String preview = stringValue(metadata, "messagePreview", "");
        return switch (request.getType()) {
            case NEW_PRIVATE_MESSAGE -> firstNonBlank(preview, "Bạn có tin nhắn mới");
            case NEW_GROUP_MESSAGE -> actorName + ": " + firstNonBlank(preview, "Đã gửi một tin nhắn");
            case GROUP_MENTION -> actorName + " đã nhắc đến bạn: " + firstNonBlank(preview, "Đã gửi một tin nhắn");
            case REPLY_TO_MY_MESSAGE -> actorName + " đã trả lời tin nhắn của bạn";
            case REACTION_TO_MY_MESSAGE -> actorName + " đã bày tỏ cảm xúc về tin nhắn của bạn";
            case INCOMING_PRIVATE_CALL -> actorName + " đang gọi cho bạn";
            case MISSED_PRIVATE_CALL -> "Bạn có cuộc gọi nhỡ từ " + actorName;
            case GROUP_CALL_STARTED -> actorName + " đã bắt đầu cuộc gọi nhóm";
            case MISSED_GROUP_CALL -> "Bạn đã bỏ lỡ một cuộc gọi nhóm";
            case FRIEND_REQUEST_RECEIVED -> actorName + " đã gửi cho bạn lời mời kết bạn";
            case FRIEND_REQUEST_ACCEPTED -> actorName + " đã chấp nhận lời mời kết bạn";
            case POST_REACTION -> actorName + " đã bày tỏ cảm xúc về bài viết của bạn";
            case POST_COMMENT -> actorName + " đã bình luận về bài viết của bạn";
            case COMMENT_REPLY -> actorName + " đã trả lời bình luận của bạn";
            case COMMENT_MENTION -> actorName + " đã nhắc đến bạn trong một bình luận";
            case POST_TAGGED -> actorName + " đã gắn thẻ bạn trong một bài viết";
            case POST_SHARED -> actorName + " đã chia sẻ bài viết của bạn";
            case REMINDER_CREATED -> actorName + " đã tạo nhắc hẹn: " + firstNonBlank(
                    stringValue(metadata, "reminderTitle", ""),
                    "Nhắc hẹn mới");
            case REMINDER_DUE -> "Đến giờ nhắc hẹn: " + firstNonBlank(
                    stringValue(metadata, "reminderTitle", ""),
                    "Bạn có nhắc hẹn đến hạn");
            case REMINDER_UPDATED -> actorName + " đã cập nhật nhắc hẹn: " + firstNonBlank(
                    stringValue(metadata, "reminderTitle", ""),
                    "Nhắc hẹn");
            case REMINDER_CANCELLED -> actorName + " đã hủy nhắc hẹn: " + firstNonBlank(
                    stringValue(metadata, "reminderTitle", ""),
                    "Nhắc hẹn");
            case GROUP_NICKNAME_CHANGED -> actorName + " đã cập nhật biệt danh trong nhóm";
            case GROUP_DISBANDED -> "Nhóm " + stringValue(metadata, "conversationName", "này") + " đã bị giải tán";
            default -> "Bạn có thông báo mới";
        };
    }

    private String defaultGenericBody(NotificationDispatchRequest request) {
        return switch (request.getType()) {
            case NEW_PRIVATE_MESSAGE -> "Bạn có tin nhắn mới";
            case NEW_GROUP_MESSAGE -> "Bạn có tin nhắn mới trong nhóm";
            case GROUP_MENTION -> "Bạn được nhắc trong một cuộc trò chuyện";
            case REPLY_TO_MY_MESSAGE -> "Bạn có một phản hồi mới";
            case REACTION_TO_MY_MESSAGE -> "Tin nhắn của bạn có cảm xúc mới";
            case INCOMING_PRIVATE_CALL -> "Bạn có cuộc gọi đến";
            case MISSED_PRIVATE_CALL -> "Bạn có cuộc gọi nhỡ";
            case GROUP_CALL_STARTED -> "Cuộc gọi nhóm đã bắt đầu";
            case MISSED_GROUP_CALL -> "Bạn đã bỏ lỡ một cuộc gọi nhóm";
            case FRIEND_REQUEST_RECEIVED -> "Bạn có lời mời kết bạn mới";
            case FRIEND_REQUEST_ACCEPTED -> "Lời mời kết bạn của bạn đã được chấp nhận";
            case POST_REACTION -> "Bài viết của bạn có tương tác mới";
            case POST_COMMENT -> "Bài viết của bạn có bình luận mới";
            case COMMENT_REPLY -> "Bình luận của bạn có phản hồi mới";
            case COMMENT_MENTION -> "Bạn được nhắc trong một bình luận";
            case POST_TAGGED -> "Bạn được gắn thẻ trong một bài viết";
            case POST_SHARED -> "Bài viết của bạn được chia sẻ";
            case REMINDER_CREATED -> "Bạn có nhắc hẹn mới";
            case REMINDER_DUE -> "Bạn có nhắc hẹn đến hạn";
            case REMINDER_UPDATED -> "Nhắc hẹn đã được cập nhật";
            case REMINDER_CANCELLED -> "Nhắc hẹn đã bị hủy";
            case GROUP_NICKNAME_CHANGED -> "Biệt danh thành viên trong nhóm đã được cập nhật";
            case GROUP_DISBANDED -> "Nhóm của bạn đã bị giải tán";
            default -> "Bạn có thông báo mới";
        };
    }

    public String buildMessagePreview(String content, String originalLinkUrl, Object messageType) {
        String type = messageType == null ? "TEXT" : String.valueOf(messageType);
        return switch (type) {
            case "IMAGE" -> "Đã gửi một hình ảnh";
            case "VIDEO" -> "Đã gửi một video";
            case "FILE" -> "Đã gửi một tệp";
            case "AUDIO" -> "Đã gửi một tin nhắn thoại";
            default -> truncate(firstNonBlank(content, originalLinkUrl, "Đã gửi một tin nhắn"));
        };
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 97) + "...";
    }

    private String stringValue(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void putIfPresent(Map<String, Object> data, String key, Object value) {
        if (value != null) {
            data.put(key, value);
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (Objects.nonNull(value)) {
            target.put(key, value);
        }
    }
}
