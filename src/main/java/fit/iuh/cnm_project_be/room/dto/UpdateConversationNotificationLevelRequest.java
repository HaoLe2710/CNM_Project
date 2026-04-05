package fit.iuh.cnm_project_be.room.dto;

import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateConversationNotificationLevelRequest {
    @NotNull
    private ConversationNotificationLevel notificationLevel;
}
