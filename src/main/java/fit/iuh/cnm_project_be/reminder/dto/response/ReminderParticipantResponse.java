package fit.iuh.cnm_project_be.reminder.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReminderParticipantResponse {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String status;
    private Instant readAt;
    private Instant acknowledgedAt;
    private Instant dismissedAt;
}
