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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRepository callRepository;
    private final UserProfileRepository userProfileRepository;
    private final FCMService fcmService;

    @Value("${SFU_URL}")
    private String sfuUrl;

    /**
     * A gọi cho B
     */
    public CallResponse initiateCall(UUID callerId, InitiateCallRequest request) {
        // Tạo channel (= roomId trên SFU)
        String channel = "call-" + UUID.randomUUID();

        Call call = new Call();
        call.setCallerId(callerId);
        call.setCalleeId(request.calleeId());
        call.setChannel(channel);
        call.setType(request.type());
        call.setStatus(CallStatus.RINGING);
        callRepository.save(call);

        // --- KHÔI PHỤC FCM ---
        // 1. Tìm thông tin người gọi để lấy Tên hiển thị
        String callerName = userProfileRepository.findById(callerId)
                .map(UserProfile::getDisplayName)
                .orElse("Người gọi ẩn danh");

        // 2. Tìm FCM Token của người nhận
        String fcmToken = userProfileRepository.findById(request.calleeId())
                .map(UserProfile::getFcmToken)
                .orElse(null);

        if (fcmToken != null && !fcmToken.trim().isEmpty()) {
            log.info("📞 Đang chuẩn bị gửi FCM tới Callee ID = {}, FCM Token = {}", request.calleeId(), fcmToken);
            
            // 3. Tiến hành gọi FCM
            fcmService.sendCallNotification(
                    fcmToken,
                    callerName,
                    call.getId().toString(),
                    channel
            );
            log.info("✅ Đã hoàn tất lệnh gọi FCMService");
        } else {
            log.warn("⚠️ Người nhận (Callee ID: {}) không có FCM Token, không thể gửi thông báo cuộc gọi!", request.calleeId());
        }
        // -----------------------

        // Trả cho người gọi thông tin kết nối SFU
        return new CallResponse(call.getId(), channel, sfuUrl);
    }

    /**
     * B chấp nhận cuộc gọi
     */
    public CallResponse acceptCall(UUID callId, UUID calleeId) {
        Call call = callRepository.findById(callId)
            .orElseThrow(() -> new RuntimeException("Call not found"));

        if (!call.getCalleeId().equals(calleeId)) {
            throw new RuntimeException("Not authorized");
        }

        call.setStatus(CallStatus.ACCEPTED);
        call.setStartedAt(Instant.now());
        callRepository.save(call);

        // Trả cho người nhận thông tin kết nối SFU (cùng channel)
        return new CallResponse(call.getId(), call.getChannel(), sfuUrl);
    }

    /**
     * Từ chối
     */
    public void rejectCall(UUID callId, UUID userId) {
        Call call = callRepository.findById(callId).orElseThrow();
        call.setStatus(CallStatus.REJECTED);
        call.setEndedAt(Instant.now());
        callRepository.save(call);
    }

    /**
     * Kết thúc cuộc gọi
     */
    public void endCall(UUID callId, UUID userId) {
        Call call = callRepository.findById(callId).orElseThrow();
        call.setStatus(CallStatus.ENDED);
        call.setEndedAt(Instant.now());
        callRepository.save(call);
    }

    /**
     * Lịch sử cuộc gọi
     */
    public List<Call> getCallHistory(UUID userId) {
        return callRepository
            .findByCallerIdOrCalleeIdOrderByCreatedAtDesc(userId, userId);
    }
}
