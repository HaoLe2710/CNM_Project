package fit.iuh.cnm_project_be.message_processing.repository;

import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import fit.iuh.cnm_project_be.message_processing.entity.MessageProcessingJob;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobScope;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobType;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageProcessingJobRepository extends BaseRepository<MessageProcessingJob, UUID> {

    Optional<MessageProcessingJob> findTopByMessageIdAndJobTypeOrderByCreatedAtDesc(
            Long messageId,
            MessageProcessingJobType jobType);

    Optional<MessageProcessingJob> findTopByAttachmentIdAndJobTypeOrderByCreatedAtDesc(
            Long attachmentId,
            MessageProcessingJobType jobType);

    @Query("""
            select j
            from MessageProcessingJob j
            where j.status = :status
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
            order by j.createdAt asc
            """)
    List<MessageProcessingJob> findRunnableByStatusOrderByCreatedAtAsc(
            @Param("status") MessageProcessingStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MessageProcessingJob j
            set j.status = :processingStatus,
                j.startedAt = :startedAt,
                j.updatedAt = :startedAt,
                j.nextAttemptAt = null,
                j.errorMessage = null
            where j.id = :jobId
              and j.status = :pendingStatus
            """)
    int claimForProcessing(
            @Param("jobId") UUID jobId,
            @Param("pendingStatus") MessageProcessingStatus pendingStatus,
            @Param("processingStatus") MessageProcessingStatus processingStatus,
            @Param("startedAt") Instant startedAt);

    @Query("""
            select j
            from MessageProcessingJob j
            where j.jobScope = :jobScope
              and j.inputStorageKey is not null
              and j.inputCleanupAt is not null
              and j.inputCleanupAt <= :now
              and j.inputCleanedAt is null
            order by j.inputCleanupAt asc, j.createdAt asc
            """)
    List<MessageProcessingJob> findDueDictationCleanupJobs(
            @Param("jobScope") MessageProcessingJobScope jobScope,
            @Param("now") Instant now,
            Pageable pageable);
}
