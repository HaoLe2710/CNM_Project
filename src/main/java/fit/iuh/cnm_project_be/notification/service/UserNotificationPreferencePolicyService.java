package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.UserNotificationPreference;
import fit.iuh.cnm_project_be.user.service.UserSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserNotificationPreferencePolicyService {

    private final UserSettingService userSettingService;

    @Transactional(readOnly = true)
    public UserNotificationPreference getPreferences(UUID userId) {
        Map<String, Object> notifications = userSettingService.getSection(userId, "notifications");
        Map<String, Object> calls = userSettingService.getSection(userId, "calls");
        Map<String, Object> timeline = userSettingService.getSection(userId, "timeline");

        return UserNotificationPreference.builder()
                .notificationsEnabled(booleanValue(notifications, "enabled", true))
                .chatNotificationsEnabled(booleanValue(notifications, "chatEnabled", true))
                .callNotificationsEnabled(booleanValue(calls, "incomingCallNotifications", true))
                .socialNotificationsEnabled(booleanValue(timeline, "showFriendUpdates", true))
                .pushEnabled(booleanValue(notifications, "pushEnabled", true))
                .previewEnabled(booleanValue(notifications, "previewEnabled", true))
                .build();
    }

    private boolean booleanValue(Map<String, Object> source, String key, boolean defaultValue) {
        if (source == null || !source.containsKey(key)) {
            return defaultValue;
        }
        Object value = source.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return defaultValue;
    }
}
