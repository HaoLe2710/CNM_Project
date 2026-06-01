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
public class MessageSearchResultResponse {
    private Long messageId;
    private UUID conversationId;
    private UUID senderId;
    private String senderDisplayName;
    private String content;
    private Instant createdAt;
}
