package fit.iuh.cnm_project_be.message_processing.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MessageProcessingJobResponse {

    private UUID id;
    private Long messageId;
    private UUID conversationId;
    private Long attachmentId;
    private String jobType;
    private String jobScope;
    private String status;
    private String provider;
    private String resultText;
    private String resultFileUrl;
    private String resultStorageKey;
    private String resultMimeType;
    private String errorMessage;
    private Integer retryCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant nextAttemptAt;
}
