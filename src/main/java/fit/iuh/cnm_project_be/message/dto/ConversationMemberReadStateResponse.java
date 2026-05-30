package fit.iuh.cnm_project_be.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberReadStateResponse {
    private UUID conversationId;
    private UUID userId;
    private Long lastDeliveredMessageId;
    private Instant deliveredAt;
    private Long lastReadMessageId;
    private Instant lastReadAt;
}
