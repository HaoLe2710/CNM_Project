package fit.iuh.cnm_project_be.notification.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.notification.dto.ConversationNotificationSettingRequest;
import fit.iuh.cnm_project_be.notification.dto.ConversationNotificationSettingResponse;
import fit.iuh.cnm_project_be.notification.service.ConversationNotificationSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationNotificationSettingController {

    private final ConversationNotificationSettingService conversationNotificationSettingService;

    @GetMapping("/{conversationId}/notification-settings")
    public ApiResponse<ConversationNotificationSettingResponse> getMyNotificationSetting(
            @PathVariable UUID conversationId) {
        return ApiResponse.ok(
                conversationNotificationSettingService.getMySetting(conversationId),
                UUID.randomUUID().toString());
    }

    @PatchMapping("/{conversationId}/notification-settings")
    public ApiResponse<ConversationNotificationSettingResponse> updateMyNotificationSetting(
            @PathVariable UUID conversationId,
            @RequestBody ConversationNotificationSettingRequest request) {
        return ApiResponse.ok(
                conversationNotificationSettingService.updateMySetting(conversationId, request),
                UUID.randomUUID().toString());
    }
}
