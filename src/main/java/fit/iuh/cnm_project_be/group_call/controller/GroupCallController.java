package fit.iuh.cnm_project_be.group_call.controller;

import fit.iuh.cnm_project_be.group_call.dto.GroupCallResponse;
import fit.iuh.cnm_project_be.group_call.dto.InitiateGroupCallRequest;
import fit.iuh.cnm_project_be.group_call.service.GroupCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/group-calls")
@RequiredArgsConstructor
@Slf4j
public class GroupCallController {

    private final GroupCallService groupCallService;

    /**
     * Khởi tạo cuộc gọi nhóm từ ô chat nhóm.
     */
    @PostMapping("/initiate")
    public ResponseEntity<GroupCallResponse> initiateGroupCall(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody InitiateGroupCallRequest request) {
        String userIdStr = jwt.getClaimAsString("userId");
        if (userIdStr == null) {
            log.error("[GroupCallController] ❌ JWT missing 'userId' claim");
            return ResponseEntity.status(401).build();
        }
        UUID userId = UUID.fromString(userIdStr);
        log.info("[GroupCallController] Received initiateGroupCall from userId: {}", userId);
        return ResponseEntity.ok(groupCallService.initiateGroupCall(userId, request));
    }

    /**
     * Tham gia cuộc gọi nhóm (bao gồm cả Late-join từ tin nhắn chat).
     */
    @PostMapping("/{groupCallId}/join")
    public ResponseEntity<?> joinGroupCall(
            @PathVariable UUID groupCallId,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
            return ResponseEntity.ok(groupCallService.joinGroupCall(groupCallId, userId));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            // Trả về trực tiếp ResponseEntity 400 để Spring Boot không in lỗi đỏ ra Terminal
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    /**
     * Rời khỏi cuộc gọi nhóm.
     */
    @PostMapping("/{groupCallId}/leave")
    public ResponseEntity<Void> leaveGroupCall(
            @PathVariable UUID groupCallId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        groupCallService.leaveGroupCall(groupCallId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Heartbeat ping (Client gọi mỗi 5s để báo vẫn đang ở trong phòng)
     */
    @PostMapping("/{groupCallId}/ping")
    public ResponseEntity<Void> pingGroupCall(
            @PathVariable UUID groupCallId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        groupCallService.pingGroupCall(groupCallId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Kiểm tra trạng thái cuộc gọi (dùng cho Late-join).
     * Nếu đã ENDED → App sẽ ẩn nút JOIN.
     */
    @GetMapping("/{groupCallId}/status")
    public ResponseEntity<GroupCallResponse> getGroupCallStatus(
            @PathVariable UUID groupCallId) {
        return ResponseEntity.ok(groupCallService.getGroupCallStatus(groupCallId));
    }
}
