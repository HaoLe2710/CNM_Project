package fit.iuh.cnm_project_be.call.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.call.dto.CallResponse;
import fit.iuh.cnm_project_be.call.dto.InitiateCallRequest;
import fit.iuh.cnm_project_be.call.entity.Call;
import fit.iuh.cnm_project_be.call.enums.CallStatus;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageStatus;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.call.repository.CallRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class CallService {

    private final CallRepository callRepository;
    private final UserProfileRepository userProfileRepository;
    private final FCMService fcmService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final MessageUserStateRepository messageUserStateRepository;
    private final ObjectMapper objectMapper;

    public CallService(
            CallRepository callRepository,
            UserProfileRepository userProfileRepository,
            FCMService fcmService,
            SimpMessagingTemplate messagingTemplate,
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            MessageRepository messageRepository,
            MessageStatusRepository messageStatusRepository,
            MessageUserStateRepository messageUserStateRepository,
            ObjectMapper objectMapper) {
        this.callRepository = callRepository;
        this.userProfileRepository = userProfileRepository;
        this.fcmService = fcmService;
        this.messagingTemplate = messagingTemplate;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
        this.messageStatusRepository = messageStatusRepository;
        this.messageUserStateRepository = messageUserStateRepository;
        this.objectMapper = objectMapper;
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
        persistPrivateCallLog(call, userId);

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
        persistPrivateCallLog(call, userId);

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

    private void persistPrivateCallLog(Call call, UUID actorUserId) {
        try {
            Optional<Conversation> conversationOptional = conversationRepository
                    .findPrivateConversationByParticipants(call.getCallerId(), call.getCalleeId());
            if (conversationOptional.isEmpty()) {
                log.warn("[CALL LOG MAP] Skipping private call log because no conversation was found for caller={} callee={}",
                        call.getCallerId(),
                        call.getCalleeId());
                return;
            }

            Conversation conversation = conversationOptional.get();
            Message callLogMessage = new Message();
            callLogMessage.setConversationId(conversation.getId());
            callLogMessage.setSenderId(call.getCallerId());
            callLogMessage.setMessageType(MessageType.CALL_LOG);

            Map<String, Object> callMetadata = new HashMap<>();
            callMetadata.put("callId", call.getId());
            callMetadata.put("callType", call.getType());
            callMetadata.put("status", call.getStatus().name());
            callMetadata.put("callerId", call.getCallerId());
            callMetadata.put("calleeId", call.getCalleeId());
            callMetadata.put("conversationType", "PRIVATE");
            callMetadata.put("startedAt", call.getStartedAt());
            callMetadata.put("endedAt", call.getEndedAt());
            callMetadata.put("durationSeconds", resolveDurationSeconds(call));
            callLogMessage.setContent(objectMapper.writeValueAsString(callMetadata));

            Message savedMessage = messageRepository.saveAndFlush(callLogMessage);
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
            Instant now = Instant.now();

            members.forEach(member -> {
                MessageStatus status = new MessageStatus();
                status.setMessageId(savedMessage.getId());
                status.setUserId(member.getUserId());
                status.setStatus(MessageDeliveryStatus.SENT);
                status.setUpdatedAt(now);
                messageStatusRepository.save(status);

                MessageUserState state = new MessageUserState();
                state.setMessageId(savedMessage.getId());
                state.setUserId(member.getUserId());
                if (member.getUserId().equals(actorUserId)) {
                    state.setSeenAt(now);
                }
                messageUserStateRepository.save(state);
            });

            String callerName = userProfileRepository.findById(call.getCallerId())
                    .map(UserProfile::getDisplayName)
                    .orElse("Người gọi");

            Map<String, Object> payload = new HashMap<>();
            payload.put("id", savedMessage.getId());
            payload.put("conversationId", savedMessage.getConversationId());
            payload.put("senderId", savedMessage.getSenderId());
            payload.put("senderDisplayName", callerName);
            payload.put("content", savedMessage.getContent());
            payload.put("type", MessageType.CALL_LOG.name());
            payload.put("createdAt", savedMessage.getCreatedAt());

            messagingTemplate.convertAndSend(
                    "/topic/conversations/" + conversation.getId(),
                    RealtimeEvent.of(RealtimeEventType.MESSAGE_CREATED, payload));
            log.info("[CALL LOG MAP] conversationId={} callId={} status={} type={}",
                    conversation.getId(),
                    call.getId(),
                    call.getStatus(),
                    call.getType());
        } catch (Exception error) {
            log.error("[CALL LOG MAP] Failed to persist private call log for callId={}: {}",
                    call.getId(),
                    error.getMessage(),
                    error);
        }
    }

    private long resolveDurationSeconds(Call call) {
        if (call.getStartedAt() == null || call.getEndedAt() == null) {
            return 0L;
        }

        long seconds = Duration.between(call.getStartedAt(), call.getEndedAt()).getSeconds();
        return Math.max(seconds, 0L);
    }
}
