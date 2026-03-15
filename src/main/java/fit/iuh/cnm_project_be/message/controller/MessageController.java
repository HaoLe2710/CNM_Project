package fit.iuh.cnm_project_be.message.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.message.dto.MessageDto;
import fit.iuh.cnm_project_be.message.dto.SendMessageRequest;
import fit.iuh.cnm_project_be.message.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ApiResponse<MessageDto> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest http) {

        UUID senderId = UUID.randomUUID();

        MessageDto message = messageService.sendMessage(senderId, request);

        return ApiResponse.ok(message, UUID.randomUUID().toString());
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<List<MessageDto>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest http) {

        List<MessageDto> messages = messageService.getMessages(conversationId, page, size);

        return ApiResponse.ok(messages, UUID.randomUUID().toString());
    }
}