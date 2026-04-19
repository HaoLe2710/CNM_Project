package fit.iuh.cnm_project_be.call.service;

import fit.iuh.cnm_project_be.call.dto.CallResponse;
import fit.iuh.cnm_project_be.call.dto.InitiateCallRequest;
import fit.iuh.cnm_project_be.call.entity.Call;
import fit.iuh.cnm_project_be.call.enums.CallStatus;
import fit.iuh.cnm_project_be.call.repository.CallRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class CallService {

    private final CallRepository callRepository;
    private final UserProfileRepository userProfileRepository;
    private final FCMService fcmService;
    private final SimpMessagingTemplate messagingTemplate;

    public CallService(
            CallRepository callRepository,
            UserProfileRepository userProfileRepository,
            FCMService fcmService,
            SimpMessagingTemplate messagingTemplate) {
        this.callRepository = callRepository;
        this.userProfileRepository = userProfileRepository;
        this.fcmService = fcmService;
        this.messagingTemplate = messagingTemplate;
    }

    @Value("${app.sfu.url}")
    private String sfuUrl;

    /**
     * A gọi cho B
     */
    public CallResponse initiateCall(UUID callerId, InitiateCallRequest request) {
        log.info("[CallService] Initiating call from {} to {}", callerId, request.calleeId());
        
        if (callerId == null || request.calleeId() == null) {
            throw new IllegalArgumentException("Caller ID or Callee ID cannot be null");
        }

        // Kiểm tra xem người gọi và người nhận có tồn tại không
        boolean callerExists = userProfileRepository.existsById(callerId);
        boolean calleeExists = userProfileRepository.existsById(request.calleeId());

        if (!callerExists || !calleeExists) {
            log.error("[CallService] ❌ Một trong hai phía không tồn tại: Caller={}, Callee={}", callerExists, calleeExists);
            throw new RuntimeException("Một trong hai người dùng không tồn tại trong hệ thống");
        }

        // Tạo channel (= roomId trên SFU)
        String channel = "call-" + UUID.randomUUID();

        Call call = new Call();
        call.setCallerId(callerId);
        call.setCalleeId(request.calleeId());
        call.setChannel(channel);
        call.setType(request.type());
        call.setStatus(CallStatus.RINGING);
        callRepository.save(call);
        log.info("[CallService] ✅ Call entity saved: {}", call.getId());

        // Đảm bảo sfuUrl có giá trị mặc định nếu @Value fail
        String currentSfuUrl = (sfuUrl != null) ? sfuUrl : "ws://192.168.1.25:4443";

        // --- Gửi thông báo ---
        // 1. Tìm thông tin người gọi
        String callerName = userProfileRepository.findById(callerId)
                .map(UserProfile::getDisplayName)
                .orElse("Người gọi ẩn danh");

        // 2. STOMP: Thông báo cho Web client qua WebSocket (Ưu tiên vì nhanh và ổn định khi app đang mở)
        try {
            Map<String, Object> stompPayload = new HashMap<>();
            stompPayload.put("callId", call.getId().toString());
            stompPayload.put("callerName", callerName);
            stompPayload.put("roomId", channel);
            stompPayload.put("sfuUrl", currentSfuUrl);
            stompPayload.put("callType", request.type().toString());
            stompPayload.put("type", request.type().toString()); // Đồng bộ tên trường cho Mobile (Legacy/Standard)

            Map<String, Object> stompEvent = new HashMap<>();
            stompEvent.put("type", "INCOMING_CALL");
            stompEvent.put("payload", stompPayload);

            messagingTemplate.convertAndSend(
                    "/topic/users/" + request.calleeId() + "/calls",
                    (Object) stompEvent
            );
            log.info("[CallService] ✅ STOMP signal (INCOMING_CALL) sent to {}", request.calleeId());
        } catch (Exception e) {
            log.error("[CallService] ❌ Failed to send STOMP signal to {}: {}", request.calleeId(), e.getMessage());
        }

        // 3. FCM: Gửi Push Notification (Để đánh thức app khi đang đóng)
        String fcmToken = userProfileRepository.findById(request.calleeId())
                .map(UserProfile::getFcmToken)
                .orElse(null);

        if (fcmToken != null && !fcmToken.trim().isEmpty()) {
            try {
                fcmService.sendCallNotification(
                        fcmToken,
                        callerName,
                        call.getId().toString(),
                        channel
                );
                log.info("[CallService] FCM Notification sent to {}", request.calleeId());
            } catch (Exception e) {
                log.warn("[CallService] FCM failed (silent fallback to STOMP): {}", e.getMessage());
            }
        }

        // Trả cho người gọi thông tin kết nối SFU
        return new CallResponse(call.getId(), channel, currentSfuUrl);
    }

    /**
     * B chấp nhận cuộc gọi
     */
    public CallResponse acceptCall(UUID callId, UUID calleeId) {
        log.info("[CallService] Accepting call: {} by user: {}", callId, calleeId);
        try {
            Call call = callRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found with ID: " + callId));

            if (!call.getCalleeId().equals(calleeId)) {
                log.error("[CallService] Unauthorized: Caller is {}, but Callee is {}", calleeId, call.getCalleeId());
                throw new RuntimeException("Not authorized to accept this call");
            }

            call.setStatus(CallStatus.ACCEPTED);
            call.setStartedAt(Instant.now());
            callRepository.save(call);

            // Đảm bảo sfuUrl có giá trị mặc định nếu @Value fail
            String currentSfuUrl = (sfuUrl != null) ? sfuUrl : "ws://192.168.1.25:4443";

            // Trả cho người nhận thông tin kết nối SFU (cùng channel)
            CallResponse response = new CallResponse(call.getId(), call.getChannel(), currentSfuUrl);

            // Thông báo cho người gọi biết cuộc gọi đã được chấp nhận
            Map<String, Object> stompPayload = new HashMap<>();
            stompPayload.put("callId", call.getId());
            stompPayload.put("status", "ACCEPTED");

            Map<String, Object> stompEvent = new HashMap<>();
            stompEvent.put("type", "CALL_ACCEPTED");
            stompEvent.put("payload", stompPayload);

            messagingTemplate.convertAndSend(
                    "/topic/users/" + call.getCallerId() + "/calls",
                    (Object) stompEvent
            );
            log.info("[CallService] STOMP CALL_ACCEPTED sent to caller: {}", call.getCallerId());

            return response;
        } catch (Exception e) {
            log.error("[CallService] Error in acceptCall: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Từ chối
     */
    public void rejectCall(UUID callId, UUID userId) {
        Call call = callRepository.findById(callId).orElseThrow();
        call.setStatus(CallStatus.REJECTED);
        call.setEndedAt(Instant.now());
        callRepository.save(call);

        // Thông báo cho người gọi biết cuộc gọi bị từ chối
        Map<String, Object> stompPayload = new HashMap<>();
        stompPayload.put("callId", call.getId());
        stompPayload.put("status", "REJECTED");

        Map<String, Object> stompEvent = new HashMap<>();
        stompEvent.put("type", "CALL_REJECTED");
        stompEvent.put("payload", stompPayload);

        messagingTemplate.convertAndSend(
                "/topic/users/" + call.getCallerId() + "/calls",
                (Object) stompEvent
        );
        log.info("Đã gửi STOMP CALL_REJECTED tới caller: {}", call.getCallerId());
    }

    /**
     * Kết thúc cuộc gọi
     */
    public void endCall(UUID callId, UUID userId) {
        Call call = callRepository.findById(callId).orElseThrow();
        call.setStatus(CallStatus.ENDED);
        call.setEndedAt(Instant.now());
        callRepository.save(call);

        // Xác định người đối diện để thông báo
        UUID otherUserId = call.getCallerId().equals(userId) ? call.getCalleeId() : call.getCallerId();

        Map<String, Object> stompPayload = new HashMap<>();
        stompPayload.put("callId", call.getId());
        stompPayload.put("status", "ENDED");

        Map<String, Object> stompEvent = new HashMap<>();
        stompEvent.put("type", "CALL_ENDED");
        stompEvent.put("payload", stompPayload);

        messagingTemplate.convertAndSend(
                "/topic/users/" + otherUserId + "/calls",
                (Object) stompEvent
        );
        log.info("Đã gửi STOMP CALL_ENDED tới đối phương: {}", otherUserId);
    }

    /**
     * Gửi tín hiệu hành động (Tắt/Bật Cam/Mic) cho người đối diện.
     */
    public void sendCallAction(UUID callId, UUID senderId, String action, String value) {
        Call call = callRepository.findById(callId).orElseThrow();
        
        // Xác định người nhận (người còn lại)
        UUID recipientId = call.getCallerId().equals(senderId) ? call.getCalleeId() : call.getCallerId();

        Map<String, Object> stompPayload = new HashMap<>();
        stompPayload.put("callId", call.getId());
        stompPayload.put("action", action);
        stompPayload.put("value", value);
        stompPayload.put("senderId", senderId);

        Map<String, Object> stompEvent = new HashMap<>();
        stompEvent.put("type", "CALL_ACTION");
        stompEvent.put("payload", stompPayload);

        log.info("[CallService] Signaling CALL_ACTION {} from {} to recipient user topic: /topic/users/{}/calls", action, senderId, recipientId);

        messagingTemplate.convertAndSend(
                "/topic/users/" + recipientId + "/calls",
                (Object) stompEvent
        );
        log.info("[CallService] Gửi CALL_ACTION ({}) từ {} tới {}", action, senderId, recipientId);
    }

    /**
     * Lịch sử cuộc gọi
     */
    public List<Call> getCallHistory(UUID userId) {
        return callRepository
            .findByCallerIdOrCalleeIdOrderByCreatedAtDesc(userId, userId);
    }
}
