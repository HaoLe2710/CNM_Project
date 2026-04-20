package fit.iuh.cnm_project_be.group_call.service;

import fit.iuh.cnm_project_be.group_call.dto.GroupCallResponse;
import fit.iuh.cnm_project_be.group_call.dto.InitiateGroupCallRequest;
import fit.iuh.cnm_project_be.group_call.entity.GroupCall;
import fit.iuh.cnm_project_be.group_call.entity.GroupCallParticipant;
import fit.iuh.cnm_project_be.group_call.enums.GroupCallStatus;
import fit.iuh.cnm_project_be.group_call.enums.ParticipantState;
import fit.iuh.cnm_project_be.group_call.repository.GroupCallParticipantRepository;
import fit.iuh.cnm_project_be.group_call.repository.GroupCallRepository;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.message.entity.MessageStatus;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class GroupCallService {

    private final GroupCallRepository groupCallRepository;
    private final GroupCallParticipantRepository participantRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserProfileRepository userProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final MessageUserStateRepository messageUserStateRepository;
    private final ObjectMapper objectMapper;

    public GroupCallService(
            GroupCallRepository groupCallRepository,
            GroupCallParticipantRepository participantRepository,
            ConversationMemberRepository conversationMemberRepository,
            UserProfileRepository userProfileRepository,
            SimpMessagingTemplate messagingTemplate,
            MessageRepository messageRepository,
            MessageStatusRepository messageStatusRepository,
            MessageUserStateRepository messageUserStateRepository,
            ObjectMapper objectMapper) {
        this.groupCallRepository = groupCallRepository;
        this.participantRepository = participantRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userProfileRepository = userProfileRepository;
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
        this.messageStatusRepository = messageStatusRepository;
        this.messageUserStateRepository = messageUserStateRepository;
        this.objectMapper = objectMapper;
    }

    @Value("${app.sfu.url}")
    private String sfuUrl;

    /**
     * Khởi tạo cuộc gọi nhóm từ ô chat nhóm.
     * Gửi tín hiệu cho tất cả thành viên trong nhóm.
     */
    @Transactional
    public GroupCallResponse initiateGroupCall(UUID initiatorId, InitiateGroupCallRequest request) {
        log.info("[GroupCallService] Initiating group call in conversation {} by user {}", request.conversationId(), initiatorId);

        // Lấy tất cả thành viên trong nhóm (tối đa 9 người)
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(request.conversationId());
        if (members.size() > 9) {
            throw new IllegalStateException("Cuộc gọi nhóm chỉ hỗ trợ tối đa 9 người tham gia.");
        }

        // Tạo GroupCall session
        String channel = "group-call-" + UUID.randomUUID();
        GroupCall groupCall = new GroupCall();
        groupCall.setConversationId(request.conversationId());
        groupCall.setInitiatorId(initiatorId);
        groupCall.setChannel(channel);
        groupCall.setStatus(GroupCallStatus.RINGING);
        groupCall.setType(request.type());
        groupCallRepository.save(groupCall);
        log.info("[GroupCallService] GroupCall created: {}", groupCall.getId());

        // Tạo bản ghi participant cho tất cả thành viên (1 hàng duy nhất mỗi người)
        boolean initiatorIncluded = false;
        for (ConversationMember member : members) {
            GroupCallParticipant participant = new GroupCallParticipant();
            participant.setGroupCall(groupCall);
            participant.setUserId(member.getUserId());
            
            // Người khởi tạo được tự động JOINED, những người khác là INVITED
            if (member.getUserId().equals(initiatorId)) {
                participant.setState(ParticipantState.JOINED);
                participant.setJoinCount(1);
                participant.setLastJoinedAt(Instant.now());
                initiatorIncluded = true;
            } else {
                participant.setState(ParticipantState.INVITED);
            }
            participantRepository.save(participant);
        }

        // Trường hợp hy hữu: Initiator không có trong danh sách member (do cache hoặc DB vướng mắc)
        if (!initiatorIncluded) {
            log.warn("[GroupCallService] ⚠️ Initiator {} not found in conversation members! Forcing add.", initiatorId);
            GroupCallParticipant host = new GroupCallParticipant();
            host.setGroupCall(groupCall);
            host.setUserId(initiatorId);
            host.setState(ParticipantState.JOINED);
            host.setJoinCount(1);
            host.setLastJoinedAt(Instant.now());
            participantRepository.save(host);
        }

        // Lấy tên người khởi tạo để hiển thị trong thông báo
        String initiatorName = userProfileRepository.findById(initiatorId)
                .map(u -> u.getDisplayName())
                .orElse("Ai đó");

        String currentSfuUrl = (sfuUrl != null) ? sfuUrl : "ws://192.168.1.25:4443";

        // Gửi tín hiệu GROUP_CALL_INCOMING tới topic của nhóm
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupCallId", groupCall.getId().toString());
        payload.put("conversationId", request.conversationId().toString());
        payload.put("initiatorId", initiatorId.toString());
        payload.put("initiatorName", initiatorName);
        payload.put("channel", channel);
        payload.put("sfuUrl", currentSfuUrl);
        payload.put("type", request.type().toString());

        Map<String, Object> event = new HashMap<>();
        event.put("type", "GROUP_CALL_INCOMING");
        event.put("payload", payload);

        try {
            log.info("[GroupCallService] Broadcasting GROUP_CALL_INCOMING to /topic/conversations/{}/calls", request.conversationId());
            messagingTemplate.convertAndSend(
                    "/topic/conversations/" + request.conversationId() + "/calls",
                    (Object) event
            );
            log.info("[GroupCallService] GROUP_CALL_INCOMING sent to conversation: {}", request.conversationId());
        } catch (Exception e) {
            log.error("[GroupCallService] ❌ Failed to send GROUP_CALL_INCOMING signal: {}", e.getMessage());
        }

        // --- LƯU TIN NHẮN CALL_LOG VÀO LỊCH SỬ CHAT (Cô lập lỗi) ---
        try {
            Message callLogMessage = new Message();
            callLogMessage.setConversationId(request.conversationId());
            callLogMessage.setSenderId(initiatorId);
            callLogMessage.setMessageType(MessageType.CALL_LOG);

            Map<String, Object> callMetadata = new HashMap<>();
            callMetadata.put("groupCallId", groupCall.getId());
            callMetadata.put("type", groupCall.getType());
            callMetadata.put("callType", groupCall.getType());
            callMetadata.put("status", "STARTED");
            callMetadata.put("callerId", initiatorId);
            callMetadata.put("conversationType", "GROUP");
            callMetadata.put("conversationId", request.conversationId());
            callMetadata.put("channel", channel);
            callMetadata.put("sfuUrl", currentSfuUrl);
            callMetadata.put("initiatorName", initiatorName);
            
            String callContentJson = objectMapper.writeValueAsString(callMetadata);
            callLogMessage.setContent(callContentJson);
            
            Message savedMsg = messageRepository.saveAndFlush(callLogMessage);

            for (ConversationMember member : members) {
                try {
                    MessageStatus status = new MessageStatus();
                    status.setMessageId(savedMsg.getId());
                    status.setUserId(member.getUserId());
                    status.setStatus(MessageDeliveryStatus.SENT);
                    status.setUpdatedAt(Instant.now());
                    messageStatusRepository.save(status);

                    MessageUserState state = new MessageUserState();
                    state.setMessageId(savedMsg.getId());
                    state.setUserId(member.getUserId());
                    if (member.getUserId().equals(initiatorId)) {
                        state.setSeenAt(Instant.now());
                    }
                    messageUserStateRepository.save(state);
                } catch (Exception innerE) {
                    log.error("[GroupCallService] ⚠️ Failed to save status/state for member {}: {}", member.getUserId(), innerE.getMessage());
                }
            }

            Map<String, Object> msgPayload = new HashMap<>();
            msgPayload.put("id", savedMsg.getId());
            msgPayload.put("conversationId", savedMsg.getConversationId());
            msgPayload.put("senderId", savedMsg.getSenderId());
            msgPayload.put("content", savedMsg.getContent());
            msgPayload.put("type", "CALL_LOG");
            msgPayload.put("createdAt", savedMsg.getCreatedAt());
            msgPayload.put("senderDisplayName", initiatorName);

            messagingTemplate.convertAndSend(
                "/topic/conversations/" + request.conversationId(),
                RealtimeEvent.of(RealtimeEventType.MESSAGE_CREATED, msgPayload)
            );

        } catch (Exception e) {
            log.error("[GroupCallService] ❌ Failed to persist call log message: {}", e.getMessage());
        }

        return new GroupCallResponse(
                groupCall.getId(),
                request.conversationId(),
                channel,
                currentSfuUrl,
                GroupCallStatus.RINGING,
                request.type()
        );
    }

    /**
     * Thành viên tham gia cuộc gọi nhóm.
     * Cập nhật trạng thái trên hàng đã có (không tạo thêm hàng mới).
     */
    @Transactional
    public GroupCallResponse joinGroupCall(UUID groupCallId, UUID userId) {
        log.info("[GroupCallService] User {} joining group call {}", userId, groupCallId);

        GroupCall groupCall = groupCallRepository.findById(groupCallId)
                .orElseThrow(() -> new RuntimeException("Cuộc gọi nhóm không tồn tại: " + groupCallId));

        if (groupCall.getStatus() == GroupCallStatus.ENDED) {
            throw new IllegalStateException("Cuộc gọi nhóm đã kết thúc.");
        }

        // Bảo mật: Kiểm tra xem user có phải thành viên của conversation này không
        boolean isMember = conversationMemberRepository.existsByConversationIdAndUserId(groupCall.getConversationId(), userId);
        if (!isMember) {
            log.warn("[GroupCallService] 🚫 User {} attempted to join group call {} without being a member of conversation {}", 
                userId, groupCallId, groupCall.getConversationId());
            throw new RuntimeException("Bạn không có quyền tham gia cuộc gọi này.");
        }

        // Cập nhật (không tạo mới) - chiến lược "1 hàng mỗi người"
        GroupCallParticipant participant = participantRepository
                .findByGroupCallIdAndUserId(groupCallId, userId)
                .orElseGet(() -> {
                    GroupCallParticipant newP = new GroupCallParticipant();
                    newP.setGroupCall(groupCall);
                    newP.setUserId(userId);
                    return newP;
                });

        participant.setState(ParticipantState.JOINED);
        participant.setJoinCount(participant.getJoinCount() + 1);
        participant.setLastJoinedAt(Instant.now());
        participantRepository.save(participant);

        // Cập nhật trạng thái cuộc gọi → ONGOING nếu có ≥2 người JOINED
        long joinedCount = participantRepository
                .findByGroupCallIdAndState(groupCallId, ParticipantState.JOINED).size();
        if (joinedCount >= 2 && groupCall.getStatus() == GroupCallStatus.RINGING) {
            groupCall.setStatus(GroupCallStatus.ONGOING);
            groupCallRepository.save(groupCall);
        }

        String currentSfuUrl = (sfuUrl != null) ? sfuUrl : "ws://192.168.1.25:4443";
        return new GroupCallResponse(
                groupCall.getId(),
                groupCall.getConversationId(),
                groupCall.getChannel(),
                currentSfuUrl,
                groupCall.getStatus(),
                groupCall.getType()
        );
    }

    /**
     * Thành viên rời cuộc gọi nhóm.
     * Cộng dồn thời gian tham gia, cập nhật state LEFT (không xóa hàng).
     */
    @Transactional
    public void leaveGroupCall(UUID groupCallId, UUID userId) {
        log.info("[GroupCallService] User {} leaving group call {}", userId, groupCallId);

        GroupCallParticipant participant = participantRepository
                .findByGroupCallIdAndUserId(groupCallId, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy participant"));

        // Cộng dồn thời gian tham gia
        if (participant.getLastJoinedAt() != null) {
            long durationSeconds = Instant.now().getEpochSecond() - participant.getLastJoinedAt().getEpochSecond();
            participant.setTotalDuration(participant.getTotalDuration() + durationSeconds);
        }
        participant.setState(ParticipantState.LEFT);
        participantRepository.save(participant);

        // Nếu không còn ai JOINED → kết thúc cuộc gọi
        long remainingJoined = participantRepository
                .findByGroupCallIdAndState(groupCallId, ParticipantState.JOINED).size();
        if (remainingJoined == 0) {
            endGroupCall(groupCallId);
        }
    }

    /**
     * Kết thúc cuộc gọi nhóm (tất cả rời hoặc Initiator kết thúc).
     */
    @Transactional
    public void endGroupCall(UUID groupCallId) {
        GroupCall groupCall = groupCallRepository.findById(groupCallId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc gọi nhóm: " + groupCallId));

        groupCall.setStatus(GroupCallStatus.ENDED);
        groupCall.setEndedAt(Instant.now());
        groupCallRepository.save(groupCall);

        // Thông báo cho tất cả thành viên biết cuộc gọi đã kết thúc
        Map<String, Object> event = new HashMap<>();
        event.put("type", "GROUP_CALL_ENDED");
        event.put("payload", Map.of("groupCallId", groupCallId.toString()));
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + groupCall.getConversationId() + "/calls",
                (Object) event
        );
        log.info("[GroupCallService] GROUP_CALL_ENDED sent for call: {}", groupCallId);
    }

    /**
     * Kiểm tra trạng thái cuộc gọi (dùng cho Late-join từ tin nhắn chat).
     */
    public GroupCallResponse getGroupCallStatus(UUID groupCallId) {
        GroupCall groupCall = groupCallRepository.findById(groupCallId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cuộc gọi nhóm: " + groupCallId));
        String currentSfuUrl = (sfuUrl != null) ? sfuUrl : "ws://192.168.1.25:4443";
        return new GroupCallResponse(
                groupCall.getId(),
                groupCall.getConversationId(),
                groupCall.getChannel(),
                currentSfuUrl,
                groupCall.getStatus(),
                groupCall.getType()
        );
    }
}
