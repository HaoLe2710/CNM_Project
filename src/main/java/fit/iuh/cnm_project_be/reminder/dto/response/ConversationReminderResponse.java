package fit.iuh.cnm_project_be.reminder.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ConversationReminderResponse {
    private UUID id;
    private UUID conversationId;
    private String conversationName;
    private UUID createdBy;
    private String createdByName;
    private String title;
    private String description;
    private Instant remindAt;
    private String timezone;
    private String status;
    private List<ReminderParticipantResponse> participants;
    private Instant dueNotifiedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private Instant createdAt;
    private Instant updatedAt;
}
