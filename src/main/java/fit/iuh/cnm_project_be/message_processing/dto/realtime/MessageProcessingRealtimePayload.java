package fit.iuh.cnm_project_be.message_processing.dto.realtime;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MessageProcessingRealtimePayload {

    private String eventType;
    private UUID jobId;
    private Long messageId;
    private UUID conversationId;
    private Long attachmentId;
    private String jobType;
    private String jobScope;
    private String status;
    private String resultText;
    private String audioUrl;
    private String errorMessage;
    private Instant occurredAt;
}
