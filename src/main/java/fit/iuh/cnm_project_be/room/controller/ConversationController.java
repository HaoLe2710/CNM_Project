package fit.iuh.cnm_project_be.room.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.room.dto.AddConversationMemberRequest;
import fit.iuh.cnm_project_be.room.dto.ArchiveConversationRequest;
import fit.iuh.cnm_project_be.room.dto.ConversationMemberRoleRequest;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.dto.CreateConversationRequest;
import fit.iuh.cnm_project_be.room.dto.MuteConversationRequest;
import fit.iuh.cnm_project_be.room.dto.PinConversationRequest;
import fit.iuh.cnm_project_be.room.dto.RenameConversationRequest;
import fit.iuh.cnm_project_be.room.dto.UpdateConversationCustomNameRequest;
import fit.iuh.cnm_project_be.room.dto.UpdateConversationNotificationLevelRequest;
import fit.iuh.cnm_project_be.room.dto.UpdateConversationAvatarRequest;
import fit.iuh.cnm_project_be.room.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<List<ConversationResponse>> getMyConversations(
            @RequestHeader("x-user-id") UUID userId,
            @RequestParam(defaultValue = "false") boolean archived) {
        List<ConversationResponse> conversations = conversationService.getMyConversations(userId, archived);
        return ApiResponse.ok(conversations, UUID.randomUUID().toString());
    }

    @GetMapping("/created-by-me")
    public ApiResponse<List<ConversationResponse>> getConversationsByMe(
            @RequestHeader("x-user-id") UUID userId,
            @RequestParam(defaultValue = "false") boolean archived) {
        List<ConversationResponse> conversations = conversationService.getConversationsCreatedByMe(userId, archived);
        return ApiResponse.ok(conversations, UUID.randomUUID().toString());
    }

    @PostMapping
    public ApiResponse<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.createConversation(userId, request), UUID.randomUUID().toString());
    }

    @PatchMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> renameConversation(
            @PathVariable UUID conversationId,
            @Valid @RequestBody RenameConversationRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.renameConversation(conversationId, userId, request.getName()), UUID.randomUUID().toString());
    }

    @PatchMapping("/{conversationId}/avatar")
    public ApiResponse<ConversationResponse> updateConversationAvatar(
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationAvatarRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.updateConversationAvatar(conversationId, userId, request.getAvatarUrl()), UUID.randomUUID().toString());
    }

    @PostMapping("/{conversationId}/transfer-ownership")
    public ApiResponse<ConversationResponse> transferOwnership(
            @PathVariable UUID conversationId,
            @Valid @RequestBody ConversationMemberRoleRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.transferOwnership(conversationId, userId, request.getUserId()), UUID.randomUUID().toString());
    }

    @PostMapping("/{conversationId}/admins")
    public ApiResponse<ConversationResponse> promoteAdmin(
            @PathVariable UUID conversationId,
            @Valid @RequestBody ConversationMemberRoleRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.promoteToAdmin(conversationId, userId, request.getUserId()), UUID.randomUUID().toString());
    }

    @DeleteMapping("/{conversationId}/admins/{targetUserId}")
    public ApiResponse<ConversationResponse> demoteAdmin(
            @PathVariable UUID conversationId,
            @PathVariable UUID targetUserId,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(conversationService.demoteAdmin(conversationId, userId, targetUserId), UUID.randomUUID().toString());
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> closeConversation(
            @PathVariable UUID conversationId,
            @RequestHeader("x-user-id") UUID userId) {
        conversationService.closeConversation(conversationId, userId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PatchMapping("/{conversationId}/mute")
    public ApiResponse<Void> updateMutePreference(
            @PathVariable UUID conversationId,
            @Valid @RequestBody MuteConversationRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        conversationService.updateMutePreference(conversationId, userId, request.getMuted());
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PatchMapping("/{conversationId}/archive")
    public ApiResponse<Void> updateArchivePreference(
            @PathVariable UUID conversationId,
            @Valid @RequestBody ArchiveConversationRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        conversationService.updateArchivePreference(conversationId, userId, request.getArchived());
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PatchMapping("/{conversationId}/pin")
    public ApiResponse<Void> updatePinPreference(
            @PathVariable UUID conversationId,
            @Valid @RequestBody PinConversationRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        conversationService.updatePinPreference(conversationId, userId, request.getPinned());
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PatchMapping("/{conversationId}/notification-level")
    public ApiResponse<Void> updateNotificationLevel(
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationNotificationLevelRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        conversationService.updateNotificationLevel(conversationId, userId, request.getNotificationLevel());
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }

    @PatchMapping("/{conversationId}/custom-name")
    public ApiResponse<Void> updateCustomName(
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationCustomNameRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        conversationService.updateCustomName(conversationId, userId, request.getCustomName());
        return ApiResponse.ok(null, UUID.randomUUID().toString());
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
