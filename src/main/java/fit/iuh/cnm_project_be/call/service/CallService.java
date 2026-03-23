package fit.iuh.cnm_project_be.call.service;


import fit.iuh.cnm_project_be.call.dto.CallResponse;
import fit.iuh.cnm_project_be.call.dto.InitiateCallRequest;
import fit.iuh.cnm_project_be.call.entity.Call;
import fit.iuh.cnm_project_be.call.enums.CallStatus;
import fit.iuh.cnm_project_be.call.repository.CallRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRepository callRepository;
    private final FCMService fcmService;        // Module notification
    private final UserService userService;       // Module user

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

//        // Gửi thông báo đến người nhận qua FCM
//        UserProfile caller = userService.getUser(callerId);
//        UserProfile callee = userService.getUser(request.calleeId());
//
//        fcmService.sendCallNotification(
//                callee.getFcmToken(),           // Token FCM của người nhận
//                caller.getDisplayName(),        // Tên người gọi
//                call.getId().toString(),
//                channel,
//                request.type().name(),
//                sfuUrl
//        );

//        // Trong UserProfile entity
//        @Column(name = "fcm_token")
//        private String fcmToken;
//
//        // Trong UserService
//        @Transactional
//        public void updateFcmToken(UUID userId, String fcmToken) {
//            UserProfile user = getUser(userId);
//            user.setFcmToken(fcmToken);
//            userProfileRepository.save(user);
//        }
//
//        // Trong UserController hoặc AuthController
//        @PostMapping("/api/users/me/fcm-token")
//        public ResponseEntity<Void> updateFcmToken(
//                @AuthenticationPrincipal UserPrincipal user,
//                @RequestBody Map<String, String> body) {
//            userService.updateFcmToken(user.getId(), body.get("token"));
//            return ResponseEntity.ok().build();
//        }




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
        // TODO: Thông báo cho caller biết bị từ chối
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
