package fit.iuh.cnm_project_be.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageReadReceiptPayload {
    private UUID conversationId;
    private UUID userId;
    private Long lastReadMessageId;
    private Instant readAt;
}
