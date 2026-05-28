package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class NotificationEventClassifier {

    private static final Set<NotificationType> CHAT_EVENTS = EnumSet.of(
            NotificationType.NEW_PRIVATE_MESSAGE,
            NotificationType.NEW_GROUP_MESSAGE,
            NotificationType.GROUP_MENTION,
            NotificationType.REPLY_TO_MY_MESSAGE,
            NotificationType.REACTION_TO_MY_MESSAGE
    );

    private static final Set<NotificationType> CALL_EVENTS = EnumSet.of(
            NotificationType.INCOMING_PRIVATE_CALL,
            NotificationType.MISSED_PRIVATE_CALL,
            NotificationType.GROUP_CALL_STARTED,
            NotificationType.MISSED_GROUP_CALL
    );

    private static final Set<NotificationType> SOCIAL_EVENTS = EnumSet.of(
            NotificationType.FRIEND_REQUEST_RECEIVED,
            NotificationType.FRIEND_REQUEST_ACCEPTED,
            NotificationType.POST_REACTION,
            NotificationType.POST_COMMENT,
            NotificationType.COMMENT_REPLY,
            NotificationType.COMMENT_MENTION,
            NotificationType.POST_TAGGED,
            NotificationType.POST_SHARED
    );

    private static final Set<NotificationType> GROUP_LIFECYCLE_EVENTS = EnumSet.of(
            NotificationType.GROUP_RENAMED,
            NotificationType.GROUP_AVATAR_CHANGED,
            NotificationType.GROUP_BACKGROUND_CHANGED,
            NotificationType.GROUP_MEMBER_ADDED,
            NotificationType.GROUP_MEMBER_REMOVED,
            NotificationType.GROUP_NICKNAME_CHANGED,
            NotificationType.GROUP_ADMIN_PROMOTED,
            NotificationType.GROUP_ADMIN_DEMOTED,
            NotificationType.GROUP_OWNER_TRANSFERRED,
            NotificationType.GROUP_LEFT,
            NotificationType.GROUP_DISBANDED
    );

    public boolean isChatEvent(NotificationType type) {
        return type != null && CHAT_EVENTS.contains(type);
    }

    public boolean isCallEvent(NotificationType type) {
        return type != null && CALL_EVENTS.contains(type);
    }

    public boolean isSocialEvent(NotificationType type) {
        return type != null && SOCIAL_EVENTS.contains(type);
    }

    public boolean isGroupLifecycleEvent(NotificationType type) {
        return type != null && GROUP_LIFECYCLE_EVENTS.contains(type);
    }

    public boolean isConversationEvent(NotificationType type) {
        return isChatEvent(type) || isCallEvent(type) || isGroupLifecycleEvent(type);
    }
}
