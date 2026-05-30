package fit.iuh.cnm_project_be.reminder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class CreateConversationReminderRequest {

    @NotBlank(message = "Reminder title is required")
    @Size(max = 255, message = "Reminder title must be at most 255 characters")
    private String title;

    @Size(max = 4000, message = "Reminder description must be at most 4000 characters")
    private String description;

    @NotNull(message = "Reminder time is required")
    private Instant remindAt;

    @Size(max = 80, message = "Timezone must be at most 80 characters")
    private String timezone;

    private List<UUID> participantIds;
}
