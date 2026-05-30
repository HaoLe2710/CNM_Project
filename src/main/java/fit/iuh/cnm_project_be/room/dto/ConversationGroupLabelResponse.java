package fit.iuh.cnm_project_be.room.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ConversationGroupLabelResponse {
    private UUID conversationId;
    private UUID userId;
    private String groupLabel;
    private String groupLabelDisplayName;
    private String groupLabelColor;
    private Instant updatedAt;
}
