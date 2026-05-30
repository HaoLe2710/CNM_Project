package fit.iuh.cnm_project_be.message_processing.entity;

import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobType;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobScope;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_processing_jobs")
@Getter
@Setter
public class MessageProcessingJob {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private MessageProcessingJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_scope", nullable = false)
    private MessageProcessingJobScope jobScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageProcessingStatus status;

    @Column(name = "provider")
    private String provider;

    @Column(name = "input_mime_type")
    private String inputMimeType;

    @Column(name = "input_storage_key")
    private String inputStorageKey;

    @Column(name = "input_language")
    private String inputLanguage;

    @Column(name = "input_voice")
    private String inputVoice;

    @Column(name = "result_text")
    private String resultText;

    @Column(name = "result_file_url")
    private String resultFileUrl;

    @Column(name = "result_storage_key")
    private String resultStorageKey;

    @Column(name = "result_mime_type")
    private String resultMimeType;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (status == null) {
            status = MessageProcessingStatus.PENDING;
        }
        if (jobScope == null) {
            jobScope = MessageProcessingJobScope.MESSAGE;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (nextAttemptAt == null && status == MessageProcessingStatus.PENDING) {
            nextAttemptAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
