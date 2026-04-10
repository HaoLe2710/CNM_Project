package fit.iuh.cnm_project_be.call.controller;

import fit.iuh.cnm_project_be.call.dto.CallResponse;
import fit.iuh.cnm_project_be.call.dto.InitiateCallRequest;
import fit.iuh.cnm_project_be.call.entity.Call;
import fit.iuh.cnm_project_be.call.service.CallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    /**
     * Tạo cuộc gọi.
     */
    @PostMapping
    public ResponseEntity<CallResponse> initiateCall(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody InitiateCallRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(callService.initiateCall(userId, request));
    }

    @PostMapping("/{callId}/accept")
    public ResponseEntity<CallResponse> acceptCall(
            @PathVariable UUID callId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(callService.acceptCall(callId, userId));
    }

    @PostMapping("/{callId}/reject")
    public ResponseEntity<Void> rejectCall(
            @PathVariable UUID callId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        callService.rejectCall(callId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{callId}/end")
    public ResponseEntity<Void> endCall(
            @PathVariable UUID callId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        callService.endCall(callId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<List<Call>> history(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(callService.getCallHistory(userId));
    }
}
