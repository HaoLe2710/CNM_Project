package fit.iuh.cnm_project_be.message_processing.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageAttachment;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message_processing.dto.request.CreateSpeechToTextJobRequest;
import fit.iuh.cnm_project_be.message_processing.dto.request.CreateTextToSpeechJobRequest;
import fit.iuh.cnm_project_be.message_processing.dto.response.MessageProcessingJobResponse;
import fit.iuh.cnm_project_be.message_processing.entity.MessageProcessingJob;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobType;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingStatus;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextResult;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechResult;
import fit.iuh.cnm_project_be.message_processing.repository.MessageProcessingJobRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageProcessingServiceTest {

    @Mock
    private MessageProcessingJobRepository messageProcessingJobRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private S3MediaStorageService s3MediaStorageService;
    @Mock
    private SpeechToTextProvider speechToTextProvider;
    @Mock
    private TextToSpeechProvider textToSpeechProvider;
    @Mock
    private MessageProcessingRealtimePublisher realtimePublisher;

    @InjectMocks
    private MessageProcessingService messageProcessingService;

    private UUID actorId;
    private UUID conversationId;
    private Long messageId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        messageId = 101L;
        ReflectionTestUtils.setField(messageProcessingService, "maxBatchSize", 10);
        ReflectionTestUtils.setField(messageProcessingService, "maxRetry", 3);
        ReflectionTestUtils.setField(messageProcessingService, "retryBaseDelayMs", 1000L);
        ReflectionTestUtils.setField(messageProcessingService, "retryMaxDelayMs", 10_000L);
        ReflectionTestUtils.setField(messageProcessingService, "maxDictationDurationMs", 90_000L);
        ReflectionTestUtils.setField(messageProcessingService, "maxDictationFileSizeBytes", 10_485_760L);
    }

    @Test
    void requestSpeechToTextAudioMessageSuccessCreatesJob() {
        Message message = buildMessage(messageId, conversationId, MessageType.AUDIO, null);
        MessageAttachment audioAttachment = buildAudioAttachment(501L, messageId);

        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, actorId)).thenReturn(true);
        when(messageAttachmentRepository.findByMessageId(messageId)).thenReturn(List.of(audioAttachment));
        when(messageProcessingJobRepository.findTopByAttachmentIdAndJobTypeOrderByCreatedAtDesc(
                audioAttachment.getId(),
                MessageProcessingJobType.STT)).thenReturn(Optional.empty());
        when(speechToTextProvider.providerName()).thenReturn("mock");
        when(messageProcessingJobRepository.save(any(MessageProcessingJob.class))).thenAnswer(invocation -> {
            MessageProcessingJob savedJob = invocation.getArgument(0);
            savedJob.setId(UUID.randomUUID());
            savedJob.setCreatedAt(Instant.now());
            savedJob.setUpdatedAt(Instant.now());
            return savedJob;
        });

        MessageProcessingJobResponse response = messageProcessingService.requestSpeechToText(
                actorId,
                messageId,
                new CreateSpeechToTextJobRequest());

        assertThat(response.getId()).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(messageId);
        assertThat(response.getAttachmentId()).isEqualTo(audioAttachment.getId());
        assertThat(response.getJobType()).isEqualTo(MessageProcessingJobType.STT.name());
        assertThat(response.getStatus()).isEqualTo(MessageProcessingStatus.PENDING.name());
    }

    @Test
    void requestSpeechToTextNonAudioMessageRejected() {
        Message message = buildMessage(messageId, conversationId, MessageType.FILE, null);
        MessageAttachment fileAttachment = new MessageAttachment();
        fileAttachment.setId(601L);
        fileAttachment.setMessageId(messageId);
        fileAttachment.setAttachmentType(MessageType.FILE);
        fileAttachment.setFileType("application/pdf");

        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, actorId)).thenReturn(true);
        when(messageAttachmentRepository.findByMessageId(messageId)).thenReturn(List.of(fileAttachment));

        assertThatThrownBy(() -> messageProcessingService.requestSpeechToText(
                actorId,
                messageId,
                new CreateSpeechToTextJobRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("audio attachment");
    }

    @Test
    void requestTextToSpeechTextMessageSuccessCreatesJob() {
        Message message = buildMessage(messageId, conversationId, MessageType.TEXT, "Xin chao");

        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, actorId)).thenReturn(true);
        when(messageProcessingJobRepository.findTopByMessageIdAndJobTypeOrderByCreatedAtDesc(
                messageId,
                MessageProcessingJobType.TTS)).thenReturn(Optional.empty());
        when(textToSpeechProvider.providerName()).thenReturn("mock");
        when(messageProcessingJobRepository.save(any(MessageProcessingJob.class))).thenAnswer(invocation -> {
            MessageProcessingJob savedJob = invocation.getArgument(0);
            savedJob.setId(UUID.randomUUID());
            savedJob.setCreatedAt(Instant.now());
            savedJob.setUpdatedAt(Instant.now());
            return savedJob;
        });

        MessageProcessingJobResponse response = messageProcessingService.requestTextToSpeech(
                actorId,
                messageId,
                new CreateTextToSpeechJobRequest());

        assertThat(response.getJobType()).isEqualTo(MessageProcessingJobType.TTS.name());
        assertThat(response.getStatus()).isEqualTo(MessageProcessingStatus.PENDING.name());
    }

    @Test
    void processSttJobSuccessSavesTranscript() {
        Long attachmentId = 701L;
        UUID jobId = UUID.randomUUID();
        MessageProcessingJob job = buildPendingJob(jobId, messageId, attachmentId, MessageProcessingJobType.STT);
        Message message = buildMessage(messageId, conversationId, MessageType.AUDIO, null);
        MessageAttachment audioAttachment = buildAudioAttachment(attachmentId, messageId);

        when(messageProcessingJobRepository.findRunnableByStatusOrderByCreatedAtAsc(
                eq(MessageProcessingStatus.PENDING),
                any(Instant.class),
                eq(PageRequest.of(0, 10)))).thenReturn(List.of(job));
        when(messageProcessingJobRepository.claimForProcessing(
                eq(jobId),
                eq(MessageProcessingStatus.PENDING),
                eq(MessageProcessingStatus.PROCESSING),
                any(Instant.class))).thenReturn(1);
        when(messageProcessingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(messageAttachmentRepository.findByMessageId(messageId)).thenReturn(List.of(audioAttachment));
        when(s3MediaStorageService.downloadByStorageKey(audioAttachment.getStorageKey())).thenReturn(new byte[] {1, 2, 3});
        when(speechToTextProvider.transcribe(any())).thenReturn(SpeechToTextResult.builder()
                .transcript("Xin chào, đây là transcript.")
                .language("vi")
                .confidence(0.8D)
                .build());
        when(messageProcessingJobRepository.save(any(MessageProcessingJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = messageProcessingService.processPendingJobs(Instant.now());

        assertThat(processed).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(MessageProcessingStatus.COMPLETED);
        assertThat(job.getResultText()).contains("transcript");
        verify(realtimePublisher).publish(actorId, job);
    }

    @Test
    void processTtsJobSuccessUploadsAudioAndSavesUrl() {
        UUID jobId = UUID.randomUUID();
        MessageProcessingJob job = buildPendingJob(jobId, messageId, null, MessageProcessingJobType.TTS);
        Message message = buildMessage(messageId, conversationId, MessageType.TEXT, "Đọc nội dung này");

        when(messageProcessingJobRepository.findRunnableByStatusOrderByCreatedAtAsc(
                eq(MessageProcessingStatus.PENDING),
                any(Instant.class),
                eq(PageRequest.of(0, 10)))).thenReturn(List.of(job));
        when(messageProcessingJobRepository.claimForProcessing(
                eq(jobId),
                eq(MessageProcessingStatus.PENDING),
                eq(MessageProcessingStatus.PROCESSING),
                any(Instant.class))).thenReturn(1);
        when(messageProcessingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(textToSpeechProvider.synthesize(any())).thenReturn(TextToSpeechResult.builder()
                .audioBytes(new byte[] {1, 2, 3, 4})
                .mimeType("audio/wav")
                .durationMs(1000L)
                .build());
        when(s3MediaStorageService.uploadBytes(
                eq(actorId),
                any(byte[].class),
                any(String.class),
                eq("audio/wav"),
                any(String.class))).thenReturn(UploadAttachmentResponse.builder()
                .url("https://cdn.example.com/tts/audio.wav")
                .storageKey("tts/" + actorId + "/2026/05/audio.wav")
                .fileName("audio.wav")
                .contentType("audio/wav")
                .fileSize(4L)
                .type(MessageType.AUDIO)
                .build());
        when(messageProcessingJobRepository.save(any(MessageProcessingJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = messageProcessingService.processPendingJobs(Instant.now());

        assertThat(processed).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(MessageProcessingStatus.COMPLETED);
        assertThat(job.getResultFileUrl()).isEqualTo("https://cdn.example.com/tts/audio.wav");
        verify(s3MediaStorageService).uploadBytes(
                eq(actorId),
                any(byte[].class),
                any(String.class),
                eq("audio/wav"),
                any(String.class));
        verify(realtimePublisher).publish(actorId, job);
    }

    @Test
    void processSttJobProviderFailureMarksFailed() {
        Long attachmentId = 801L;
        UUID jobId = UUID.randomUUID();
        MessageProcessingJob job = buildPendingJob(jobId, messageId, attachmentId, MessageProcessingJobType.STT);
        Message message = buildMessage(messageId, conversationId, MessageType.AUDIO, null);
        MessageAttachment audioAttachment = buildAudioAttachment(attachmentId, messageId);

        ReflectionTestUtils.setField(messageProcessingService, "maxRetry", 1);

        when(messageProcessingJobRepository.findRunnableByStatusOrderByCreatedAtAsc(
                eq(MessageProcessingStatus.PENDING),
                any(Instant.class),
                eq(PageRequest.of(0, 10)))).thenReturn(List.of(job));
        when(messageProcessingJobRepository.claimForProcessing(
                eq(jobId),
                eq(MessageProcessingStatus.PENDING),
                eq(MessageProcessingStatus.PROCESSING),
                any(Instant.class))).thenReturn(1);
        when(messageProcessingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(messageAttachmentRepository.findByMessageId(messageId)).thenReturn(List.of(audioAttachment));
        when(s3MediaStorageService.downloadByStorageKey(audioAttachment.getStorageKey())).thenReturn(new byte[] {4, 5, 6});
        when(speechToTextProvider.transcribe(any()))
                .thenThrow(new BusinessException("Speech-to-text provider is not configured"));
        when(messageProcessingJobRepository.save(any(MessageProcessingJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = messageProcessingService.processPendingJobs(Instant.now());

        assertThat(processed).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(MessageProcessingStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("not configured");
        verify(realtimePublisher).publish(actorId, job);
    }

    @Test
    void getJobNonMemberForbidden() {
        UUID jobId = UUID.randomUUID();
        MessageProcessingJob job = buildPendingJob(jobId, messageId, null, MessageProcessingJobType.TTS);
        Message message = buildMessage(messageId, conversationId, MessageType.TEXT, "Hello");

        when(messageProcessingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, actorId)).thenReturn(false);

        assertThatThrownBy(() -> messageProcessingService.getJob(actorId, jobId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void retryFailedJobSuccess() {
        UUID jobId = UUID.randomUUID();
        MessageProcessingJob failedJob = buildPendingJob(jobId, messageId, null, MessageProcessingJobType.TTS);
        failedJob.setStatus(MessageProcessingStatus.FAILED);
        failedJob.setRetryCount(1);
        failedJob.setErrorMessage("old error");
        Message message = buildMessage(messageId, conversationId, MessageType.TEXT, "hello");

        when(messageProcessingJobRepository.findById(jobId)).thenReturn(Optional.of(failedJob));
        when(messageRepository.findByIdAndDeletedAtIsNull(messageId)).thenReturn(Optional.of(message));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, actorId)).thenReturn(true);
        when(messageProcessingJobRepository.save(any(MessageProcessingJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MessageProcessingJobResponse response = messageProcessingService.retryJob(actorId, jobId);

        assertThat(response.getStatus()).isEqualTo(MessageProcessingStatus.PENDING.name());
        assertThat(response.getRetryCount()).isEqualTo(1);
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void requestDictationSpeechToTextSuccessCreatesDictationJob() {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "dictation.webm",
                "audio/webm",
                new byte[] {1, 2, 3, 4});
        fit.iuh.cnm_project_be.message_processing.dto.request.CreateDictationSpeechToTextRequest request =
                new fit.iuh.cnm_project_be.message_processing.dto.request.CreateDictationSpeechToTextRequest();
        request.setConversationId(conversationId);
        request.setLanguage("vi");
        request.setAudioFormat("webm");
        request.setDurationMs(5_000L);

        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, actorId)).thenReturn(true);
        when(speechToTextProvider.providerName()).thenReturn("openrouter");
        when(s3MediaStorageService.upload(eq(actorId), eq(audio), any(String.class)))
                .thenReturn(UploadAttachmentResponse.builder()
                        .url("https://cdn.example.com/dictation/sample.webm")
                        .storageKey("dictation/2026/05/" + actorId + "/sample.webm")
                        .contentType("audio/webm")
                        .fileName("dictation.webm")
                        .fileSize(4L)
                        .type(MessageType.AUDIO)
                        .build());
        when(messageProcessingJobRepository.save(any(MessageProcessingJob.class))).thenAnswer(invocation -> {
            MessageProcessingJob savedJob = invocation.getArgument(0);
            savedJob.setId(UUID.randomUUID());
            savedJob.setCreatedAt(Instant.now());
            savedJob.setUpdatedAt(Instant.now());
            return savedJob;
        });

        MessageProcessingJobResponse response = messageProcessingService.requestDictationSpeechToText(
                actorId,
                audio,
                request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getMessageId()).isNull();
        assertThat(response.getConversationId()).isEqualTo(conversationId);
        assertThat(response.getJobScope()).isEqualTo("DICTATION");
        assertThat(response.getJobType()).isEqualTo("STT");
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void requestDictationSpeechToTextNonMemberForbidden() {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "dictation.webm",
                "audio/webm",
                new byte[] {1, 2, 3});
        fit.iuh.cnm_project_be.message_processing.dto.request.CreateDictationSpeechToTextRequest request =
                new fit.iuh.cnm_project_be.message_processing.dto.request.CreateDictationSpeechToTextRequest();
        request.setConversationId(conversationId);

        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, actorId)).thenReturn(false);

        assertThatThrownBy(() -> messageProcessingService.requestDictationSpeechToText(actorId, audio, request))
                .isInstanceOf(ForbiddenException.class);
    }

    private Message buildMessage(Long id, UUID conversationId, MessageType type, String content) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setSenderId(actorId);
        message.setMessageType(type);
        message.setContent(content);
        message.setCreatedAt(Instant.now());
        return message;
    }

    private MessageAttachment buildAudioAttachment(Long attachmentId, Long ownerMessageId) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setId(attachmentId);
        attachment.setMessageId(ownerMessageId);
        attachment.setAttachmentType(MessageType.AUDIO);
        attachment.setFileType("audio/mpeg");
        attachment.setStorageKey("chat/" + actorId + "/voice.mp3");
        attachment.setFileUrl("https://cdn.example.com/voice.mp3");
        attachment.setDurationMs(10_000L);
        attachment.setAudioFormat("mp3");
        return attachment;
    }

    private MessageProcessingJob buildPendingJob(
            UUID jobId,
            Long ownerMessageId,
            Long attachmentId,
            MessageProcessingJobType jobType) {
        MessageProcessingJob job = new MessageProcessingJob();
        job.setId(jobId);
        job.setMessageId(ownerMessageId);
        job.setAttachmentId(attachmentId);
        job.setJobType(jobType);
        job.setStatus(MessageProcessingStatus.PENDING);
        job.setProvider("mock");
        job.setRetryCount(0);
        job.setRequestedBy(actorId);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job.setNextAttemptAt(Instant.now());
        return job;
    }
}
