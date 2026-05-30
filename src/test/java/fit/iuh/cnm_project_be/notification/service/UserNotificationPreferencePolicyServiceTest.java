package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.notification.dto.UserNotificationPreference;
import fit.iuh.cnm_project_be.user.service.UserSettingService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserNotificationPreferencePolicyServiceTest {

    @Test
    void missingOptionalFieldsDefaultAllow() {
        UUID userId = UUID.randomUUID();
        UserSettingService userSettingService = mock(UserSettingService.class);
        when(userSettingService.getSection(userId, "notifications")).thenReturn(Map.of());
        when(userSettingService.getSection(userId, "calls")).thenReturn(Map.of());
        when(userSettingService.getSection(userId, "timeline")).thenReturn(Map.of());

        UserNotificationPreference preference = new UserNotificationPreferencePolicyService(userSettingService)
                .getPreferences(userId);

        assertThat(preference.isNotificationsEnabled()).isTrue();
        assertThat(preference.isChatNotificationsEnabled()).isTrue();
        assertThat(preference.isCallNotificationsEnabled()).isTrue();
        assertThat(preference.isSocialNotificationsEnabled()).isTrue();
        assertThat(preference.isPushEnabled()).isTrue();
        assertThat(preference.isPreviewEnabled()).isTrue();
    }

    @Test
    void mapsExistingJsonFields() {
        UUID userId = UUID.randomUUID();
        UserSettingService userSettingService = mock(UserSettingService.class);
        when(userSettingService.getSection(userId, "notifications")).thenReturn(Map.of(
                "enabled", false,
                "chatEnabled", false,
                "pushEnabled", false,
                "previewEnabled", false
        ));
        when(userSettingService.getSection(userId, "calls")).thenReturn(Map.of(
                "incomingCallNotifications", false
        ));
        when(userSettingService.getSection(userId, "timeline")).thenReturn(Map.of(
                "showFriendUpdates", false
        ));

        UserNotificationPreference preference = new UserNotificationPreferencePolicyService(userSettingService)
                .getPreferences(userId);

        assertThat(preference.isNotificationsEnabled()).isFalse();
        assertThat(preference.isChatNotificationsEnabled()).isFalse();
        assertThat(preference.isCallNotificationsEnabled()).isFalse();
        assertThat(preference.isSocialNotificationsEnabled()).isFalse();
        assertThat(preference.isPushEnabled()).isFalse();
        assertThat(preference.isPreviewEnabled()).isFalse();
    }
}
