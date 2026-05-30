package fit.iuh.cnm_project_be.message_processing.service;

import fit.iuh.cnm_project_be.message_processing.dto.realtime.MessageProcessingRealtimePayload;
import fit.iuh.cnm_project_be.message_processing.entity.MessageProcessingJob;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MessageProcessingRealtimePublisher {

    public static final String MESSAGE_PROCESSING_UPDATED = "MESSAGE_PROCESSING_UPDATED";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(UUID userId, MessageProcessingJob job) {
        if (userId == null || job == null) {
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/users/" + userId + "/message-processing",
                MessageProcessingRealtimePayload.builder()
                        .eventType(MESSAGE_PROCESSING_UPDATED)
                        .jobId(job.getId())
                        .messageId(job.getMessageId())
                        .conversationId(job.getConversationId())
                        .attachmentId(job.getAttachmentId())
                        .jobType(job.getJobType() != null ? job.getJobType().name() : null)
                        .jobScope(job.getJobScope() != null ? job.getJobScope().name() : null)
                        .status(job.getStatus() != null ? job.getStatus().name() : null)
                        .resultText(job.getResultText())
                        .audioUrl(job.getResultFileUrl())
                        .errorMessage(job.getErrorMessage())
                        .occurredAt(Instant.now())
                        .build());
    }
}
