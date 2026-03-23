package fit.iuh.cnm_project_be.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDeletedPayload {

    private Long messageId;
    private UUID conversationId;
    private Instant deletedAt;
}
