package fit.iuh.cnm_project_be.room.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.room.dto.AddConversationMemberRequest;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.dto.CreateConversationRequest;
import fit.iuh.cnm_project_be.room.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    public ApiResponse<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.createConversation(userId, request), UUID.randomUUID().toString());
    }

    @PostMapping("/{conversationId}/members")
    public ApiResponse<ConversationResponse> addMember(
            @PathVariable UUID conversationId,
            @Valid @RequestBody AddConversationMemberRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.addMember(conversationId, userId, request.getUserId()), UUID.randomUUID().toString());
    }

    @DeleteMapping("/{conversationId}/members/{memberUserId}")
    public ApiResponse<ConversationResponse> removeMember(
            @PathVariable UUID conversationId,
            @PathVariable UUID memberUserId,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.removeMember(conversationId, userId, memberUserId), UUID.randomUUID().toString());
    }

    @PostMapping("/{conversationId}/leave")
    public ApiResponse<Void> leaveConversation(
            @PathVariable UUID conversationId,
            @RequestHeader("x-user-id") UUID userId) {
        conversationService.leaveConversation(conversationId, userId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }
}
