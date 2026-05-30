package fit.iuh.cnm_project_be.message_processing.repository;

import fit.iuh.cnm_project_be.message_processing.entity.MessageProcessingJob;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobScope;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobType;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.docker.compose.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class MessageProcessingJobRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MessageProcessingJobRepository messageProcessingJobRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                create table if not exists message_processing_jobs (
                    id uuid primary key,
                    message_id bigint null,
                    conversation_id uuid null,
                    attachment_id bigint null,
                    job_type varchar(30) not null,
                    job_scope varchar(30) not null default 'MESSAGE',
                    status varchar(30) not null,
                    provider varchar(50) null,
                    input_mime_type varchar(120) null,
                    input_storage_key varchar(1000) null,
                    input_language varchar(30) null,
                    input_voice varchar(80) null,
                    result_text clob null,
                    result_file_url varchar(1000) null,
                    result_storage_key varchar(1000) null,
                    result_mime_type varchar(120) null,
                    error_message clob null,
                    retry_count int not null default 0,
                    requested_by uuid not null,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    started_at timestamp null,
                    completed_at timestamp null,
                    next_attempt_at timestamp null,
                    input_cleanup_at timestamp null,
                    input_cleaned_at timestamp null,
                    input_cleanup_error clob null
                )
                """);
        jdbcTemplate.execute("delete from message_processing_jobs");
    }

    @Test
    void findLatestByMessageAndTypeSuccess() {
        UUID actorId = UUID.randomUUID();
        saveJob(100L, 1001L, MessageProcessingJobType.STT, MessageProcessingStatus.COMPLETED, actorId, Instant.now().minusSeconds(30));
        MessageProcessingJob newer = saveJob(100L, 1001L, MessageProcessingJobType.STT, MessageProcessingStatus.FAILED, actorId, Instant.now());

        MessageProcessingJob latest = messageProcessingJobRepository
                .findTopByMessageIdAndJobTypeOrderByCreatedAtDesc(100L, MessageProcessingJobType.STT)
                .orElseThrow();

        assertThat(latest.getId()).isEqualTo(newer.getId());
    }

    @Test
    void findPendingJobsSuccess() {
        UUID actorId = UUID.randomUUID();
        saveJob(200L, null, MessageProcessingJobType.TTS, MessageProcessingStatus.PENDING, actorId, Instant.now().minusSeconds(20));
        saveJob(201L, null, MessageProcessingJobType.TTS, MessageProcessingStatus.PROCESSING, actorId, Instant.now().minusSeconds(10));
        saveJob(202L, null, MessageProcessingJobType.TTS, MessageProcessingStatus.PENDING, actorId, Instant.now().minusSeconds(1));

        List<MessageProcessingJob> pending = messageProcessingJobRepository.findRunnableByStatusOrderByCreatedAtAsc(
                MessageProcessingStatus.PENDING,
                Instant.now(),
                PageRequest.of(0, 10));

        assertThat(pending).hasSize(2);
        assertThat(pending).extracting(MessageProcessingJob::getMessageId)
                .containsExactly(200L, 202L);
    }

    @Test
    @Transactional
    void claimForProcessingTransitionsState() {
        UUID actorId = UUID.randomUUID();
        MessageProcessingJob pendingJob = saveJob(
                300L,
                null,
                MessageProcessingJobType.TTS,
                MessageProcessingStatus.PENDING,
                actorId,
                Instant.now().minusSeconds(5));

        int updated = messageProcessingJobRepository.claimForProcessing(
                pendingJob.getId(),
                MessageProcessingStatus.PENDING,
                MessageProcessingStatus.PROCESSING,
                Instant.now());

        assertThat(updated).isEqualTo(1);
        MessageProcessingJob refreshed = messageProcessingJobRepository.findById(pendingJob.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(MessageProcessingStatus.PROCESSING);
        assertThat(refreshed.getStartedAt()).isNotNull();
    }

    @Test
    void findDueDictationCleanupJobsReturnsOnlyDueUncleanedDictationRows() {
        UUID actorId = UUID.randomUUID();
        MessageProcessingJob dueDictation = saveJob(
                null,
                null,
                MessageProcessingJobType.STT,
                MessageProcessingStatus.COMPLETED,
                actorId,
                Instant.now().minusSeconds(30));
        dueDictation.setJobScope(MessageProcessingJobScope.DICTATION);
        dueDictation.setInputStorageKey("dictation/a.webm");
        dueDictation.setInputCleanupAt(Instant.now().minusSeconds(10));
        dueDictation.setInputCleanedAt(null);
        messageProcessingJobRepository.saveAndFlush(dueDictation);

        MessageProcessingJob futureDictation = saveJob(
                null,
                null,
                MessageProcessingJobType.STT,
                MessageProcessingStatus.COMPLETED,
                actorId,
                Instant.now().minusSeconds(20));
        futureDictation.setJobScope(MessageProcessingJobScope.DICTATION);
        futureDictation.setInputStorageKey("dictation/b.webm");
        futureDictation.setInputCleanupAt(Instant.now().plusSeconds(60));
        messageProcessingJobRepository.saveAndFlush(futureDictation);

        List<MessageProcessingJob> dueRows = messageProcessingJobRepository.findDueDictationCleanupJobs(
                MessageProcessingJobScope.DICTATION,
                Instant.now(),
                PageRequest.of(0, 10));

        assertThat(dueRows).hasSize(1);
        assertThat(dueRows.get(0).getId()).isEqualTo(dueDictation.getId());
    }

    private MessageProcessingJob saveJob(
            Long messageId,
            Long attachmentId,
            MessageProcessingJobType jobType,
            MessageProcessingStatus status,
            UUID requestedBy,
            Instant createdAt) {
        MessageProcessingJob job = new MessageProcessingJob();
        job.setMessageId(messageId);
        job.setAttachmentId(attachmentId);
        job.setJobType(jobType);
        job.setStatus(status);
        job.setProvider("mock");
        job.setRetryCount(0);
        job.setRequestedBy(requestedBy);
        job.setCreatedAt(createdAt);
        job.setUpdatedAt(createdAt);
        job.setNextAttemptAt(createdAt);
        return messageProcessingJobRepository.saveAndFlush(job);
    }
}
