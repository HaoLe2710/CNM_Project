package fit.iuh.cnm_project_be.message.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.message.dto.CursorPageResponse;
import fit.iuh.cnm_project_be.message.dto.EditMessageRequest;
import fit.iuh.cnm_project_be.message.dto.MessageContextResponse;
import fit.iuh.cnm_project_be.message.dto.PinMessageRequest;
import fit.iuh.cnm_project_be.message.dto.MessageReactionRequest;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.dto.TypingRealtimePayload;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public ApiResponse<MessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(messageService.sendMessage(currentUserId, request), UUID.randomUUID().toString());
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<CursorPageResponse<MessageResponse>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(messageService.getMessages(conversationId, currentUserId, cursor, size), UUID.randomUUID().toString());
    }

    @GetMapping("/{conversationId}/context")
    public ApiResponse<MessageContextResponse> getMessageContext(
            @PathVariable UUID conversationId,
            @RequestParam Long messageId,
            @RequestParam(defaultValue = "50") int range,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(
                messageService.getMessageContext(conversationId, messageId, currentUserId, range),
                UUID.randomUUID().toString()
        );
    }

    @PatchMapping("/{messageId}")
    public ApiResponse<MessageResponse> editMessage(
            @PathVariable Long messageId,
            @Valid @RequestBody EditMessageRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(messageService.editMessage(messageId, currentUserId, request), UUID.randomUUID().toString());
    }

    @PatchMapping("/{messageId}/pin")
    public ApiResponse<MessageResponse> updatePinState(
            @PathVariable Long messageId,
            @Valid @RequestBody PinMessageRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(messageService.updatePinState(messageId, currentUserId, request.getPinned()), UUID.randomUUID().toString());
    }

    @PatchMapping("/{messageId}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long messageId,
            @RequestParam MessageDeliveryStatus status,
            @RequestHeader("x-user-id") UUID currentUserId) {
        messageService.updateStatus(messageId, currentUserId, status);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PostMapping("/typing/{conversationId}")
    public ApiResponse<Void> sendTypingIndicator(
            @PathVariable UUID conversationId,
            @RequestParam boolean isTyping,
            @RequestHeader("x-user-id") UUID currentUserId) {

        log.info("[BE TYPING RECEIVE] conversationId={} userId={} isTyping={}",
                conversationId, currentUserId, isTyping);
        messageService.assertConversationAccess(conversationId, currentUserId);
        TypingRealtimePayload payload =
                messageService.buildTypingRealtimePayload(conversationId, currentUserId, isTyping);
        String topic = "/topic/typing/" + conversationId;
        log.info("[BE TYPING BROADCAST] topic={} payload={}", topic, payload);
        messagingTemplate.convertAndSend(topic,
                RealtimeEvent.of(RealtimeEventType.TYPING_UPDATED, payload));

        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PatchMapping("/mark-seen/{conversationId}")
    public ApiResponse<Void> markAsSeen(
            @PathVariable UUID conversationId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        messageService.markAsSeen(conversationId, currentUserId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> deleteMessage(
            @PathVariable Long messageId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        messageService.deleteMessage(messageId, currentUserId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PutMapping("/{messageId}/reaction")
    public ApiResponse<Void> addOrUpdateReaction(
            @PathVariable Long messageId,
            @Valid @RequestBody MessageReactionRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        messageService.addOrUpdateReaction(messageId, currentUserId, request);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @DeleteMapping("/{messageId}/reaction")
    public ApiResponse<Void> removeReaction(
            @PathVariable Long messageId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        messageService.removeReaction(messageId, currentUserId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PostMapping("/{messageId}/hide")
    public ApiResponse<Void> hideMessage(
            @PathVariable Long messageId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        messageService.hideMessage(messageId, currentUserId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PatchMapping("/{messageId}/remove-for-me")
    public ApiResponse<Void> removeMessageForMe(
            @PathVariable Long messageId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        messageService.removeMessageForMe(messageId, currentUserId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PostMapping(value = "/attachments/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadAttachmentResponse> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(messageService.uploadAttachment(currentUserId, file), UUID.randomUUID().toString());
    }
}
