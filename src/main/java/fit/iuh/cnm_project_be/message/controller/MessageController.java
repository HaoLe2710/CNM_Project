package fit.iuh.cnm_project_be.message.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.message.dto.MessageDto;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.dto.TypingRequest;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public ApiResponse<MessageDto> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader("x-user-id") UUID currentUserId) {
        return ApiResponse.ok(messageService.sendMessage(currentUserId, request), UUID.randomUUID().toString());
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<Slice<MessageDto>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(messageService.getMessages(conversationId, page), UUID.randomUUID().toString());
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

        TypingRequest payload = new TypingRequest(currentUserId, isTyping);
        messagingTemplate.convertAndSend("/topic/typing/" + conversationId, payload);

        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PatchMapping("/mark-seen/{conversationId}")
    public ApiResponse<Void> markAsSeen(
            @PathVariable UUID conversationId,
            @RequestHeader("x-user-id") UUID currentUserId) {
        messageService.markAsSeen(conversationId, currentUserId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }
}