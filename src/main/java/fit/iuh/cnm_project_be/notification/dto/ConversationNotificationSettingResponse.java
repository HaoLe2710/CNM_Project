package fit.iuh.cnm_project_be.notification.dto;

import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ConversationNotificationSettingResponse {
    private UUID conversationId;
    private UUID userId;
    private ConversationNotificationLevel notificationLevel;
    private Instant mutedUntil;
    private boolean muted;
    private Instant lastMutedAt;
    private Instant updatedAt;
}
