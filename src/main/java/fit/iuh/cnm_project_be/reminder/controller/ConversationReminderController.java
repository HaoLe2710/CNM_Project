package fit.iuh.cnm_project_be.reminder.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.reminder.dto.request.CreateConversationReminderRequest;
import fit.iuh.cnm_project_be.reminder.dto.request.UpdateConversationReminderRequest;
import fit.iuh.cnm_project_be.reminder.dto.response.ConversationReminderResponse;
import fit.iuh.cnm_project_be.reminder.dto.response.ReminderPageResponse;
import fit.iuh.cnm_project_be.reminder.service.ConversationReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ConversationReminderController {

    private final ConversationReminderService reminderService;

    @PostMapping("/api/v1/conversations/{conversationId}/reminders")
    public ApiResponse<ConversationReminderResponse> createReminder(
            @PathVariable UUID conversationId,
            @Valid @RequestBody CreateConversationReminderRequest request,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(
                reminderService.createReminder(conversationId, userId, request),
                UUID.randomUUID().toString());
    }

    @GetMapping("/api/v1/conversations/{conversationId}/reminders")
    public ApiResponse<ReminderPageResponse> getConversationReminders(
            @PathVariable UUID conversationId,
            @RequestHeader("x-user-id") UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(
                reminderService.getConversationReminders(
                        conversationId,
                        userId,
                        status,
                        from,
                        to,
                        page,
                        size),
                UUID.randomUUID().toString());
    }

    @GetMapping("/api/v1/reminders")
    public ApiResponse<ReminderPageResponse> getMyReminders(
            @RequestHeader("x-user-id") UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(
                reminderService.getMyReminders(userId, status, scope, from, to, page, size),
                UUID.randomUUID().toString());
    }

    @PatchMapping("/api/v1/reminders/{reminderId}")
    public ApiResponse<ConversationReminderResponse> updateReminder(
            @PathVariable UUID reminderId,
            @RequestHeader("x-user-id") UUID userId,
            @Valid @RequestBody UpdateConversationReminderRequest request) {
        return ApiResponse.ok(
                reminderService.updateReminder(reminderId, userId, request),
                UUID.randomUUID().toString());
    }

    @DeleteMapping("/api/v1/reminders/{reminderId}")
    public ApiResponse<ConversationReminderResponse> deleteReminder(
            @PathVariable UUID reminderId,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(
                reminderService.deleteReminder(reminderId, userId),
                UUID.randomUUID().toString());
    }

    @PostMapping("/api/v1/reminders/{reminderId}/cancel")
    public ApiResponse<ConversationReminderResponse> cancelReminder(
            @PathVariable UUID reminderId,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(
                reminderService.cancelReminder(reminderId, userId),
                UUID.randomUUID().toString());
    }

    @PostMapping("/api/v1/reminders/{reminderId}/complete")
    public ApiResponse<ConversationReminderResponse> completeReminder(
            @PathVariable UUID reminderId,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(
                reminderService.completeReminder(reminderId, userId),
                UUID.randomUUID().toString());
    }

    @PostMapping("/api/v1/reminders/{reminderId}/ack")
    public ApiResponse<ConversationReminderResponse> acknowledgeReminder(
            @PathVariable UUID reminderId,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(
                reminderService.acknowledgeReminder(reminderId, userId),
                UUID.randomUUID().toString());
    }

    @PostMapping("/api/v1/reminders/{reminderId}/dismiss")
    public ApiResponse<ConversationReminderResponse> dismissReminder(
            @PathVariable UUID reminderId,
            @RequestHeader("x-user-id") UUID userId) {
        return ApiResponse.ok(
                reminderService.dismissReminder(reminderId, userId),
                UUID.randomUUID().toString());
    }
}
