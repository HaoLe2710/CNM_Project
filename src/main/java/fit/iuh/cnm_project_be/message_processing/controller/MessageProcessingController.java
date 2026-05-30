package fit.iuh.cnm_project_be.message_processing.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message_processing.dto.request.CreateDictationSpeechToTextRequest;
import fit.iuh.cnm_project_be.message_processing.dto.request.CreateSpeechToTextJobRequest;
import fit.iuh.cnm_project_be.message_processing.dto.request.CreateTextToSpeechJobRequest;
import fit.iuh.cnm_project_be.message_processing.dto.response.MessageProcessingJobResponse;
import fit.iuh.cnm_project_be.message_processing.enums.MessageProcessingJobType;
import fit.iuh.cnm_project_be.message_processing.service.MessageProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessageProcessingController {

    private final MessageProcessingService messageProcessingService;

    @PostMapping("/messages/{messageId}/processing/stt")
    public ApiResponse<MessageProcessingJobResponse> requestSpeechToText(
            @PathVariable Long messageId,
            @RequestHeader("x-user-id") UUID currentUserId,
            @RequestBody(required = false) CreateSpeechToTextJobRequest request) {
        CreateSpeechToTextJobRequest payload = request != null ? request : new CreateSpeechToTextJobRequest();
        return ApiResponse.ok(
                messageProcessingService.requestSpeechToText(currentUserId, messageId, payload),
                UUID.randomUUID().toString());
    }

    @PostMapping("/messages/{messageId}/processing/tts")
    public ApiResponse<MessageProcessingJobResponse> requestTextToSpeech(
            @PathVariable Long messageId,
            @RequestHeader("x-user-id") UUID currentUserId,
            @RequestBody(required = false) CreateTextToSpeechJobRequest request) {
        CreateTextToSpeechJobRequest payload = request != null ? request : new CreateTextToSpeechJobRequest();
        return ApiResponse.ok(
                messageProcessingService.requestTextToSpeech(currentUserId, messageId, payload),
                UUID.randomUUID().toString());
    }

    @PostMapping(value = "/message-processing/dictation/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MessageProcessingJobResponse> requestDictationSpeechToText(
            @RequestHeader("x-user-id") UUID currentUserId,
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam UUID conversationId,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String audioFormat,
            @RequestParam(required = false) Long durationMs) {
        CreateDictationSpeechToTextRequest payload = new CreateDictationSpeechToTextRequest();
        payload.setConversationId(conversationId);
        payload.setLanguage(language);
        payload.setAudioFormat(audioFormat);
        payload.setDurationMs(durationMs);

        return ApiResponse.ok(
                messageProcessingService.requestDictationSpeechToText(currentUserId, audioFile, payload),
                UUID.randomUUID().toString());
    }

    @GetMapping("/messages/{messageId}/processing/latest")
    public ApiResponse<MessageProcessingJobResponse> getLatestMessageProcessing(
            @PathVariable Long messageId,
            @RequestHeader("x-user-id") UUID currentUserId,
            @RequestParam String jobType,
            @RequestParam(required = false) Long attachmentId) {
        MessageProcessingJobType resolvedJobType = MessageProcessingJobType.fromCode(jobType)
                .orElseThrow(() -> new BusinessException("Invalid job type"));
        return ApiResponse.ok(
                messageProcessingService.getLatest(currentUserId, messageId, resolvedJobType, attachmentId),
                UUID.randomUUID().toString());
    }

    @GetMapping("/message-processing/jobs/{jobId}")
    public ApiResponse<MessageProcessingJobResponse> getProcessingJob(
            @PathVariable UUID jobId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(
                messageProcessingService.getJob(currentUserId, jobId),
                UUID.randomUUID().toString());
    }

    @PostMapping("/message-processing/jobs/{jobId}/retry")
    public ApiResponse<MessageProcessingJobResponse> retryProcessingJob(
            @PathVariable UUID jobId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(
                messageProcessingService.retryJob(currentUserId, jobId),
                UUID.randomUUID().toString());
    }
}
