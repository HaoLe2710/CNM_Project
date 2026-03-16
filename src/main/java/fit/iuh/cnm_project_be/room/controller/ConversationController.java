package fit.iuh.cnm_project_be.room.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ApiResponse<List<ConversationResponse>> getMyConversations(@RequestHeader("x-user-id") UUID userId) {
        List<ConversationResponse> conversations = conversationService.getMyConversations(userId);
        return ApiResponse.ok(conversations, UUID.randomUUID().toString());
    }

    @GetMapping("/created-by-me")
    public ApiResponse<List<ConversationResponse>> getConversationsByMe(@RequestHeader("x-user-id") UUID userId) {
        List<ConversationResponse> conversations = conversationService.getConversationsCreatedByMe(userId);
        return ApiResponse.ok(conversations, UUID.randomUUID().toString());
    }
}