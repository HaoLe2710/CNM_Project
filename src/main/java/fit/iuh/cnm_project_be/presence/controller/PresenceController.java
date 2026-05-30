package fit.iuh.cnm_project_be.presence.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.presence.dto.request.BatchPresenceRequest;
import fit.iuh.cnm_project_be.presence.dto.response.BatchPresenceResponse;
import fit.iuh.cnm_project_be.presence.dto.response.ConversationPresenceResponse;
import fit.iuh.cnm_project_be.presence.dto.response.PresenceItemResponse;
import fit.iuh.cnm_project_be.presence.service.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping("/users/{userId}")
    public ApiResponse<PresenceItemResponse> getUserPresence(@PathVariable UUID userId) {
        return ApiResponse.ok(
                presenceService.getPresence(userId),
                UUID.randomUUID().toString());
    }

    @PostMapping("/users/batch")
    public ApiResponse<BatchPresenceResponse> getBatchPresence(
            @Valid @RequestBody BatchPresenceRequest request) {
        return ApiResponse.ok(
                presenceService.getBatchPresence(request.getUserIds()),
                UUID.randomUUID().toString());
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationPresenceResponse> getConversationPresence(
            @PathVariable UUID conversationId) {
        return ApiResponse.ok(
                presenceService.getConversationPresence(conversationId),
                UUID.randomUUID().toString());
    }
}

