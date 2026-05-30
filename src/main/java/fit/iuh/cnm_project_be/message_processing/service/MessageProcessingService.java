package fit.iuh.cnm_project_be.message_processing.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageAttachment;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageAttachmentRepository;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message_processing.dto.request.CreateDictationSpeechToTextRequest;
import fit.iuh.cnm_project_be.message_processing.dto.request.CreateSpeechToTextJobRequest;
import fit.iuh.cnm_project_be.message_processing.dto.request.CreateTextToSpeechJobRequest;
import fit.iuh.cnm_project_be.message_processing.dto.response.MessageProcessingJobResponse;
import fit.iuh.cnm_project_be.message_processing.entity.MessageProcessingJob;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobType;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobScope;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingStatus;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextRequest;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextResult;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechRequest;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechResult;
import fit.iuh.cnm_project_be.message_processing.repository.MessageProcessingJobRepository;
import fit.iuh.cnm_project_be.message_processing.provider.impl.OpenAiProviderSupport;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageProcessingService {

    private static final int MAX_TEXT_TO_SPEECH_LENGTH = 2000;
    private static final String DEFAULT_LANGUAGE = "vi";
    private static final String TTS_PATH_PREFIX = "tts";
    private static final String DICTATION_PATH_PREFIX = "dictation";
    private static final long MAX_STT_DURATION_MS = 300_000L;

    private final MessageProcessingJobRepository messageProcessingJobRepository;
    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final S3MediaStorageService s3MediaStorageService;
    private final SpeechToTextProvider speechToTextProvider;
    private final TextToSpeechProvider textToSpeechProvider;
    private final MessageProcessingRealtimePublisher realtimePublisher;

    @Value("${app.message-processing.max-batch-size:10}")
    private int maxBatchSize;

    @Value("${app.message-processing.max-retry:3}")
    private int maxRetry;

    @Value("${app.message-processing.retry-base-delay-ms:5000}")
    private long retryBaseDelayMs;

    @Value("${app.message-processing.retry-max-delay-ms:60000}")
    private long retryMaxDelayMs;

    @Value("${app.message-processing.dictation.max-duration-ms:90000}")
    private long maxDictationDurationMs;

    @Value("${app.message-processing.dictation.max-file-size-bytes:10485760}")
    private long maxDictationFileSizeBytes;

    private final HttpClient directHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Transactional
    public MessageProcessingJobResponse requestSpeechToText(
            UUID actorId,
            Long messageId,
            CreateSpeechToTextJobRequest request) {
        Message message = getAccessibleMessageOrThrow(actorId, messageId);
        MessageAttachment selectedAttachment = resolveAudioAttachment(message, request != null ? request.getAttachmentId() : null);
        String language = normalizeNullableText(request != null ? request.getLanguage() : null);
        if (language == null) {
            language = DEFAULT_LANGUAGE;
        }

        boolean forceRefresh = request != null && Boolean.TRUE.equals(request.getForceRefresh());
        Optional<MessageProcessingJob> latestJob = messageProcessingJobRepository
                .findTopByAttachmentIdAndJobTypeOrderByCreatedAtDesc(
                        selectedAttachment.getId(),
                        MessageProcessingJobType.STT);

        if (!forceRefresh && latestJob.isPresent()) {
            MessageProcessingStatus currentStatus = latestJob.get().getStatus();
            if (currentStatus == MessageProcessingStatus.COMPLETED
                    || currentStatus == MessageProcessingStatus.PENDING
                    || currentStatus == MessageProcessingStatus.PROCESSING) {
                return toResponse(latestJob.get());
            }
        }

        MessageProcessingJob newJob = new MessageProcessingJob();
        newJob.setMessageId(messageId);
        newJob.setConversationId(message.getConversationId());
        newJob.setAttachmentId(selectedAttachment.getId());
        newJob.setJobType(MessageProcessingJobType.STT);
        newJob.setJobScope(MessageProcessingJobScope.MESSAGE);
        newJob.setStatus(MessageProcessingStatus.PENDING);
        newJob.setProvider(speechToTextProvider.providerName());
        newJob.setInputMimeType(normalizeNullableText(selectedAttachment.getFileType()));
        newJob.setInputStorageKey(normalizeNullableText(selectedAttachment.getStorageKey()));
        newJob.setInputLanguage(language);
        newJob.setRequestedBy(actorId);
        newJob.setNextAttemptAt(Instant.now());
        return toResponse(messageProcessingJobRepository.save(newJob));
    }

    @Transactional
    public MessageProcessingJobResponse requestTextToSpeech(
            UUID actorId,
            Long messageId,
            CreateTextToSpeechJobRequest request) {
        Message message = getAccessibleMessageOrThrow(actorId, messageId);
        String sourceText = normalizeNullableText(message.getContent());
        if (sourceText == null) {
            throw new BusinessException("Text-to-speech is only available for text messages");
        }
        if (sourceText.length() > MAX_TEXT_TO_SPEECH_LENGTH) {
            throw new BusinessException("Text message is too long for text-to-speech");
        }
        if (message.getMessageType() != MessageType.TEXT) {
            throw new BusinessException("Text-to-speech is only available for text messages");
        }

        boolean forceRefresh = request != null && Boolean.TRUE.equals(request.getForceRefresh());
        String language = normalizeNullableText(request != null ? request.getLanguage() : null);
        if (language == null) {
            language = DEFAULT_LANGUAGE;
        }
        String voice = normalizeNullableText(request != null ? request.getVoice() : null);
        if (voice == null) {
            voice = "default";
        }
        Optional<MessageProcessingJob> latestJob = messageProcessingJobRepository
                .findTopByMessageIdAndJobTypeOrderByCreatedAtDesc(messageId, MessageProcessingJobType.TTS);
        if (!forceRefresh && latestJob.isPresent()) {
            MessageProcessingStatus currentStatus = latestJob.get().getStatus();
            if (currentStatus == MessageProcessingStatus.COMPLETED
                    || currentStatus == MessageProcessingStatus.PENDING
                    || currentStatus == MessageProcessingStatus.PROCESSING) {
                return toResponse(latestJob.get());
            }
        }

        MessageProcessingJob newJob = new MessageProcessingJob();
        newJob.setMessageId(messageId);
        newJob.setConversationId(message.getConversationId());
        newJob.setAttachmentId(null);
        newJob.setJobType(MessageProcessingJobType.TTS);
        newJob.setJobScope(MessageProcessingJobScope.MESSAGE);
        newJob.setStatus(MessageProcessingStatus.PENDING);
        newJob.setProvider(textToSpeechProvider.providerName());
        newJob.setInputMimeType("text/plain");
        newJob.setInputStorageKey(null);
        newJob.setInputLanguage(language);
        newJob.setInputVoice(voice);
        newJob.setRequestedBy(actorId);
        newJob.setNextAttemptAt(Instant.now());
        return toResponse(messageProcessingJobRepository.save(newJob));
    }

    @Transactional
    public MessageProcessingJobResponse requestDictationSpeechToText(
            UUID actorId,
            MultipartFile audioFile,
            CreateDictationSpeechToTextRequest request) {
        if (request == null || request.getConversationId() == null) {
            throw new BusinessException("Conversation ID is required");
        }
        if (!conversationMemberRepository.existsByConversationIdAndUserId(request.getConversationId(), actorId)) {
            throw new ForbiddenException("User does not belong to this conversation");
        }
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException("Dictation audio is required");
        }

        long fileSize = audioFile.getSize();
        if (fileSize <= 0) {
            throw new BusinessException("Dictation audio is empty");
        }
        if (fileSize > Math.max(maxDictationFileSizeBytes, 1L)) {
            throw new BusinessException("Dictation audio exceeds the allowed size");
        }

        Long durationMs = request.getDurationMs();
        if (durationMs != null && durationMs > Math.max(maxDictationDurationMs, 1L)) {
            throw new BusinessException("Dictation duration exceeds the supported limit");
        }

        String language = normalizeNullableText(request.getLanguage());
        if (language == null) {
            language = DEFAULT_LANGUAGE;
        }
        String normalizedMimeType = normalizeNullableText(audioFile.getContentType());
        String normalizedAudioFormat = OpenAiProviderSupport.sanitizeAudioExtension(
                audioFile.getOriginalFilename(),
                request.getAudioFormat(),
                normalizedMimeType);
        OpenAiProviderSupport.validateSttAudioFormat(
                audioFile.getOriginalFilename(),
                normalizedAudioFormat,
                normalizedMimeType);

        UploadAttachmentResponse uploadResult = s3MediaStorageService.upload(
                actorId,
                audioFile,
                buildDictationPathPrefix());

        MessageProcessingJob newJob = new MessageProcessingJob();
        newJob.setMessageId(null);
        newJob.setConversationId(request.getConversationId());
        newJob.setAttachmentId(null);
        newJob.setJobType(MessageProcessingJobType.STT);
        newJob.setJobScope(MessageProcessingJobScope.DICTATION);
        newJob.setStatus(MessageProcessingStatus.PENDING);
        newJob.setProvider(speechToTextProvider.providerName());
        newJob.setInputMimeType(normalizeNullableText(uploadResult.getContentType()));
        newJob.setInputStorageKey(normalizeNullableText(uploadResult.getStorageKey()));
        newJob.setInputLanguage(language);
        newJob.setRequestedBy(actorId);
        newJob.setNextAttemptAt(Instant.now());
        return toResponse(messageProcessingJobRepository.save(newJob));
    }

    @Transactional(readOnly = true)
    public MessageProcessingJobResponse getJob(UUID actorId, UUID jobId) {
        MessageProcessingJob job = getJobOrThrow(jobId);
        assertActorCanAccessJob(actorId, job);
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public MessageProcessingJobResponse getLatest(
            UUID actorId,
            Long messageId,
            MessageProcessingJobType jobType,
            Long attachmentId) {
        if (jobType == null) {
            throw new BusinessException("Job type is required");
        }

        Message message = getAccessibleMessageOrThrow(actorId, messageId);
        Optional<MessageProcessingJob> latestJob = switch (jobType) {
            case STT -> {
                if (attachmentId != null) {
                    yield messageProcessingJobRepository
                            .findTopByAttachmentIdAndJobTypeOrderByCreatedAtDesc(attachmentId, jobType);
                }
                MessageAttachment attachment = resolveAudioAttachment(message, null);
                yield messageProcessingJobRepository
                        .findTopByAttachmentIdAndJobTypeOrderByCreatedAtDesc(attachment.getId(), jobType);
            }
            case TTS -> messageProcessingJobRepository
                    .findTopByMessageIdAndJobTypeOrderByCreatedAtDesc(messageId, jobType);
        };

        return latestJob.map(this::toResponse).orElse(null);
    }

    @Transactional
    public MessageProcessingJobResponse retryJob(UUID actorId, UUID jobId) {
        MessageProcessingJob job = getJobOrThrow(jobId);
        assertActorCanAccessJob(actorId, job);
        if (job.getStatus() != MessageProcessingStatus.FAILED) {
            throw new BusinessException("Only failed jobs can be retried");
        }
        job.setStatus(MessageProcessingStatus.PENDING);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setNextAttemptAt(Instant.now());
        return toResponse(messageProcessingJobRepository.save(job));
    }

    @Transactional
    public int processPendingJobs(Instant now) {
        Instant executionTime = now != null ? now : Instant.now();
        int batchSize = Math.max(maxBatchSize, 1);
        List<MessageProcessingJob> pendingJobs = messageProcessingJobRepository
                .findRunnableByStatusOrderByCreatedAtAsc(
                        MessageProcessingStatus.PENDING,
                        executionTime,
                        PageRequest.of(0, batchSize));

        if (pendingJobs.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (MessageProcessingJob pendingJob : pendingJobs) {
            try {
                if (processSingleJob(pendingJob.getId(), executionTime)) {
                    processed++;
                }
            } catch (Exception ex) {
                log.warn("[MessageProcessing] Failed to process jobId={} reason={}",
                        pendingJob.getId(),
                        ex.getMessage());
            }
        }
        return processed;
    }

    @Transactional
    protected boolean processSingleJob(UUID jobId, Instant startedAt) {
        Instant startedTimestamp = startedAt != null ? startedAt : Instant.now();
        int claimed = messageProcessingJobRepository.claimForProcessing(
                jobId,
                MessageProcessingStatus.PENDING,
                MessageProcessingStatus.PROCESSING,
                startedTimestamp);
        if (claimed == 0) {
            return false;
        }

        MessageProcessingJob job = getJobOrThrow(jobId);
        try {
            if (job.getJobType() == MessageProcessingJobType.STT) {
                processSpeechToText(job);
            } else if (job.getJobType() == MessageProcessingJobType.TTS) {
                processTextToSpeech(job, startedTimestamp);
            } else {
                throw new BusinessException("Unsupported processing job type");
            }

            job.setStatus(MessageProcessingStatus.COMPLETED);
            job.setErrorMessage(null);
            job.setCompletedAt(Instant.now());
            job.setNextAttemptAt(null);
            messageProcessingJobRepository.save(job);
            realtimePublisher.publish(job.getRequestedBy(), job);
            return true;
        } catch (Exception ex) {
            Instant failureTime = Instant.now();
            int failedCount = (job.getRetryCount() != null ? job.getRetryCount() : 0) + 1;
            job.setRetryCount(failedCount);
            job.setErrorMessage(resolveErrorMessage(ex));
            job.setCompletedAt(failureTime);
            if (failedCount >= Math.max(maxRetry, 1)) {
                job.setStatus(MessageProcessingStatus.FAILED);
                job.setNextAttemptAt(null);
                messageProcessingJobRepository.save(job);
                realtimePublisher.publish(job.getRequestedBy(), job);
                return true;
            }

            job.setStatus(MessageProcessingStatus.PENDING);
            job.setNextAttemptAt(failureTime.plusMillis(resolveRetryDelayMs(failedCount)));
            messageProcessingJobRepository.save(job);
            return true;
        }
    }

    private void processSpeechToText(MessageProcessingJob job) {
        MessageProcessingJobScope scope = resolveJobScope(job);
        SpeechToTextResult result;

        if (scope == MessageProcessingJobScope.DICTATION) {
            result = processDictationSpeechToText(job);
        } else {
            result = processMessageSpeechToText(job);
        }

        String transcript = normalizeNullableText(result != null ? result.getTranscript() : null);
        if (transcript == null) {
            throw new BusinessException("Speech-to-text provider returned an empty transcript");
        }

        job.setResultText(transcript);
        job.setResultMimeType("text/plain");
        job.setResultFileUrl(null);
        job.setResultStorageKey(null);
    }

    private SpeechToTextResult processMessageSpeechToText(MessageProcessingJob job) {
        Message message = messageRepository.findByIdAndDeletedAtIsNull(job.getMessageId())
                .orElseThrow(() -> new NotFoundException("Message not found"));
        MessageAttachment attachment = resolveAudioAttachment(message, job.getAttachmentId());

        String language = normalizeNullableText(job.getInputLanguage());
        if (language == null) {
            language = DEFAULT_LANGUAGE;
        }

        return speechToTextProvider.transcribe(SpeechToTextRequest.builder()
                .messageId(job.getMessageId())
                .attachmentId(attachment.getId())
                .fileUrl(attachment.getFileUrl())
                .storageKey(attachment.getStorageKey())
                .fileName(attachment.getOriginalFileName())
                .mimeType(attachment.getFileType())
                .audioFormat(attachment.getAudioFormat())
                .durationMs(attachment.getDurationMs())
                .language(language)
                .audioBytes(loadAttachmentAudioBytes(attachment))
                .build());
    }

    private SpeechToTextResult processDictationSpeechToText(MessageProcessingJob job) {
        if (job.getConversationId() == null) {
            throw new BusinessException("Dictation job is missing conversation context");
        }
        String storageKey = normalizeNullableText(job.getInputStorageKey());
        if (storageKey == null) {
            throw new BusinessException("Dictation audio source is unavailable");
        }

        String language = normalizeNullableText(job.getInputLanguage());
        if (language == null) {
            language = DEFAULT_LANGUAGE;
        }
        String mimeType = normalizeNullableText(job.getInputMimeType());
        String format = OpenAiProviderSupport.sanitizeAudioExtension(
                storageKey,
                null,
                mimeType);

        return speechToTextProvider.transcribe(SpeechToTextRequest.builder()
                .messageId(null)
                .attachmentId(null)
                .fileUrl(null)
                .storageKey(storageKey)
                .fileName(storageKey)
                .mimeType(mimeType)
                .audioFormat(format)
                .durationMs(null)
                .language(language)
                .audioBytes(s3MediaStorageService.downloadByStorageKey(storageKey))
                .build());
    }

    private void processTextToSpeech(MessageProcessingJob job, Instant processingTime) {
        Message message = messageRepository.findByIdAndDeletedAtIsNull(job.getMessageId())
                .orElseThrow(() -> new NotFoundException("Message not found"));
        String sourceText = normalizeNullableText(message.getContent());
        if (sourceText == null) {
            throw new BusinessException("Text-to-speech cannot process empty text");
        }

        String language = normalizeNullableText(job.getInputLanguage());
        if (language == null) {
            language = DEFAULT_LANGUAGE;
        }
        String voice = normalizeNullableText(job.getInputVoice());
        if (voice == null) {
            voice = "default";
        }
        TextToSpeechResult result = textToSpeechProvider.synthesize(TextToSpeechRequest.builder()
                .messageId(job.getMessageId())
                .text(sourceText)
                .language(language)
                .voice(voice)
                .build());

        byte[] audioBytes = result != null ? result.getAudioBytes() : null;
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException("Text-to-speech provider returned empty audio");
        }

        String mimeType = normalizeNullableText(result != null ? result.getMimeType() : null);
        if (mimeType == null) {
            mimeType = "audio/wav";
        }
        String fileExtension = resolveFileExtension(mimeType);
        String fileName = String.format(
                Locale.ROOT,
                "tts-message-%d-%s.%s",
                job.getMessageId(),
                job.getId(),
                fileExtension);
        String pathPrefix = buildTtsPathPrefix(job.getRequestedBy(), processingTime);

        UploadAttachmentResponse uploadResult = s3MediaStorageService.uploadBytes(
                job.getRequestedBy(),
                audioBytes,
                fileName,
                mimeType,
                pathPrefix);

        job.setResultText(null);
        job.setResultFileUrl(uploadResult.getUrl());
        job.setResultStorageKey(uploadResult.getStorageKey());
        job.setResultMimeType(mimeType);
    }

    private MessageAttachment resolveAudioAttachment(Message message, Long attachmentId) {
        List<MessageAttachment> attachments = messageAttachmentRepository.findByMessageId(message.getId());
        if (attachments == null || attachments.isEmpty()) {
            throw new BusinessException("Message does not contain attachments");
        }

        if (attachmentId != null) {
            MessageAttachment selectedAttachment = attachments.stream()
                    .filter(attachment -> attachment.getId().equals(attachmentId))
                    .filter(this::isAudioAttachment)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Audio attachment not found"));
            validateAudioAttachmentForStt(selectedAttachment);
            return selectedAttachment;
        }

        MessageAttachment resolvedAttachment = attachments.stream()
                .filter(this::isAudioAttachment)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Message does not contain an audio attachment"));
        validateAudioAttachmentForStt(resolvedAttachment);
        return resolvedAttachment;
    }

    private boolean isAudioAttachment(MessageAttachment attachment) {
        if (attachment == null) {
            return false;
        }
        if (attachment.getAttachmentType() == MessageType.AUDIO) {
            return true;
        }
        String contentType = normalizeNullableText(attachment.getFileType());
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("audio/");
    }

    private MessageProcessingJobScope resolveJobScope(MessageProcessingJob job) {
        if (job == null || job.getJobScope() == null) {
            return MessageProcessingJobScope.MESSAGE;
        }
        return job.getJobScope();
    }

    private void assertActorCanAccessJob(UUID actorId, MessageProcessingJob job) {
        MessageProcessingJobScope scope = resolveJobScope(job);
        if (scope == MessageProcessingJobScope.DICTATION) {
            if (job.getRequestedBy() == null || !job.getRequestedBy().equals(actorId)) {
                throw new ForbiddenException("User cannot access this processing job");
            }
            if (job.getConversationId() == null
                    || !conversationMemberRepository.existsByConversationIdAndUserId(job.getConversationId(), actorId)) {
                throw new ForbiddenException("User does not belong to this conversation");
            }
            return;
        }

        if (job.getMessageId() == null) {
            throw new BusinessException("Processing job is missing message context");
        }
        getAccessibleMessageOrThrow(actorId, job.getMessageId());
    }

    private Message getAccessibleMessageOrThrow(UUID actorId, Long messageId) {
        Message message = messageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        boolean member = conversationMemberRepository.existsByConversationIdAndUserId(
                message.getConversationId(),
                actorId);
        if (!member) {
            throw new ForbiddenException("User does not belong to this conversation");
        }
        return message;
    }

    private String buildDictationPathPrefix() {
        LocalDate dateUtc = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        return String.format(
                Locale.ROOT,
                "%s/%04d/%02d",
                DICTATION_PATH_PREFIX,
                dateUtc.getYear(),
                dateUtc.getMonthValue());
    }

    private MessageProcessingJob getJobOrThrow(UUID jobId) {
        return messageProcessingJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Processing job not found"));
    }

    private String resolveFileExtension(String mimeType) {
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        if (normalized.contains("mpeg") || normalized.contains("mp3")) {
            return "mp3";
        }
        if (normalized.contains("ogg")) {
            return "ogg";
        }
        if (normalized.contains("webm")) {
            return "webm";
        }
        if (normalized.contains("mp4") || normalized.contains("m4a")) {
            return "m4a";
        }
        if (normalized.contains("wav")) {
            return "wav";
        }
        return "bin";
    }

    private String buildTtsPathPrefix(UUID userId, Instant timestamp) {
        LocalDate dateUtc = (timestamp != null ? timestamp : Instant.now()).atZone(ZoneOffset.UTC).toLocalDate();
        return String.format(
                Locale.ROOT,
                "%s/%s/%04d/%02d",
                TTS_PATH_PREFIX,
                userId,
                dateUtc.getYear(),
                dateUtc.getMonthValue());
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveErrorMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "Unknown processing error";
        }
        return ex.getMessage().trim();
    }

    private long resolveRetryDelayMs(int failedCount) {
        long baseDelay = Math.max(retryBaseDelayMs, 1000L);
        long maxDelay = Math.max(retryMaxDelayMs, baseDelay);
        long exponent = Math.max(failedCount - 1L, 0L);
        long multiplier = 1L << Math.min(exponent, 8L);
        long computedDelay = baseDelay * multiplier;
        return Math.min(computedDelay, maxDelay);
    }

    private void validateAudioAttachmentForStt(MessageAttachment attachment) {
        if (attachment == null) {
            throw new BusinessException("Audio attachment not found");
        }
        Long durationMs = attachment.getDurationMs();
        if (durationMs != null && durationMs > MAX_STT_DURATION_MS) {
            throw new BusinessException("Audio duration exceeds the supported limit");
        }
        OpenAiProviderSupport.validateSttAudioFormat(
                attachment.getOriginalFileName(),
                attachment.getAudioFormat(),
                attachment.getFileType());
    }

    private byte[] loadAttachmentAudioBytes(MessageAttachment attachment) {
        String storageKey = normalizeNullableText(attachment.getStorageKey());
        if (storageKey != null) {
            try {
                return s3MediaStorageService.downloadByStorageKey(storageKey);
            } catch (Exception ex) {
                log.warn("[MessageProcessing] Failed to load audio by storage key, fallback to URL. attachmentId={} reason={}",
                        attachment.getId(), ex.getMessage());
            }
        }

        String fileUrl = normalizeNullableText(attachment.getFileUrl());
        if (fileUrl == null) {
            throw new BusinessException("Audio source is unavailable for processing");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(fileUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = directHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2 || response.body() == null || response.body().length == 0) {
                throw new BusinessException("Unable to fetch audio content for speech-to-text");
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Audio retrieval was interrupted");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Unable to fetch audio content for speech-to-text");
        }
    }

    private MessageProcessingJobResponse toResponse(MessageProcessingJob job) {
        return MessageProcessingJobResponse.builder()
                .id(job.getId())
                .messageId(job.getMessageId())
                .conversationId(job.getConversationId())
                .attachmentId(job.getAttachmentId())
                .jobType(job.getJobType() != null ? job.getJobType().name() : null)
                .jobScope(job.getJobScope() != null ? job.getJobScope().name() : null)
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .provider(job.getProvider())
                .resultText(job.getResultText())
                .resultFileUrl(job.getResultFileUrl())
                .resultStorageKey(job.getResultStorageKey())
                .resultMimeType(job.getResultMimeType())
                .errorMessage(job.getErrorMessage())
                .retryCount(job.getRetryCount())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .nextAttemptAt(job.getNextAttemptAt())
                .build();
    }
}
