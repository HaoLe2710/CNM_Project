package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import lombok.Data;

import java.time.Instant;

@Data
public class ConversationNotificationSettingRequest {
    private ConversationNotificationLevel notificationLevel;
    private Instant mutedUntil;
}
