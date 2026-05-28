package fit.iuh.cnm_project_be.notification.controller;

import fit.iuh.cnm_project_be.common.api.ApiResponse;
import fit.iuh.cnm_project_be.notification.dto.NotificationPageResponse;
import fit.iuh.cnm_project_be.notification.dto.NotificationResponse;
import fit.iuh.cnm_project_be.notification.dto.UnreadCountResponse;
import fit.iuh.cnm_project_be.notification.service.NotificationQueryService;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final UserService userService;

    @GetMapping
    public ApiResponse<NotificationPageResponse> getMyNotifications(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        UUID currentUserId = userService.getCurrentUserId();
        return ApiResponse.ok(
                notificationQueryService.getMyNotifications(currentUserId, cursor, limit, unreadOnly),
                UUID.randomUUID().toString());
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount() {
        UUID currentUserId = userService.getCurrentUserId();
        return ApiResponse.ok(notificationQueryService.getUnreadCount(currentUserId), UUID.randomUUID().toString());
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markAsRead(@PathVariable UUID notificationId) {
        UUID currentUserId = userService.getCurrentUserId();
        return ApiResponse.ok(
                notificationQueryService.markAsRead(currentUserId, notificationId),
                UUID.randomUUID().toString());
    }

    @PatchMapping("/read-all")
    public ApiResponse<UnreadCountResponse> markAllAsRead() {
        UUID currentUserId = userService.getCurrentUserId();
        return ApiResponse.ok(notificationQueryService.markAllAsRead(currentUserId), UUID.randomUUID().toString());
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> deleteNotification(@PathVariable UUID notificationId) {
        UUID currentUserId = userService.getCurrentUserId();
        notificationQueryService.deleteNotification(currentUserId, notificationId);
        return ApiResponse.ok(null, UUID.randomUUID().toString());
    }
}
