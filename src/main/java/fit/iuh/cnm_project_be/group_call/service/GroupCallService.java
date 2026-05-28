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
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final NotificationDispatcher notificationDispatcher;

    public GroupCallService(
            GroupCallRepository groupCallRepository,
            GroupCallParticipantRepository participantRepository,
            ConversationMemberRepository conversationMemberRepository,
            UserProfileRepository userProfileRepository,
            SimpMessagingTemplate messagingTemplate,
            MessageRepository messageRepository,
            MessageStatusRepository messageStatusRepository,
            MessageUserStateRepository messageUserStateRepository,
            ObjectMapper objectMapper,
            NotificationDispatcher notificationDispatcher) {
        this.groupCallRepository = groupCallRepository;
        this.participantRepository = participantRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userProfileRepository = userProfileRepository;
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
        this.messageStatusRepository = messageStatusRepository;
        this.messageUserStateRepository = messageUserStateRepository;
        this.objectMapper = objectMapper;
        this.notificationDispatcher = notificationDispatcher;
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
        safeDispatchGroupCallStartedNotification(groupCall, initiatorId, initiatorName, channel, currentSfuUrl, members);

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

    private void safeDispatchGroupCallStartedNotification(
            GroupCall groupCall,
            UUID initiatorId,
            String initiatorName,
            String channel,
            String currentSfuUrl,
            List<ConversationMember> members) {
        if (notificationDispatcher == null) {
            return;
        }
        try {
            List<UUID> recipients = members.stream()
                    .map(ConversationMember::getUserId)
                    .filter(userId -> !userId.equals(initiatorId))
                    .distinct()
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }
            notificationDispatcher.dispatch(NotificationDispatchRequest.builder()
                    .type(NotificationType.GROUP_CALL_STARTED)
                    .targetType(NotificationTargetType.CALL)
                    .targetId(groupCall.getId())
                    .actorId(initiatorId)
                    .explicitRecipientIds(recipients)
                    .conversationId(groupCall.getConversationId())
                    .metadata(Map.of(
                            "actorName", initiatorName,
                            "conversationName", "Cuộc gọi nhóm",
                            "groupCallId", groupCall.getId().toString(),
                            "channel", channel,
                            "sfuUrl", currentSfuUrl,
                            "callType", groupCall.getType() != null ? groupCall.getType().name() : ""
                    ))
                    .dedupKeyPrefix("group-call:" + groupCall.getId() + ":started")
                    .recipientDirectlyAffected(true)
                    .build());
        } catch (Exception ex) {
            log.warn("[GroupCallService] Notification dispatch failed for groupCallId={}: {}",
                    groupCall.getId(),
                    ex.getMessage());
        }
    }

    private void safeDispatchMissedGroupCallNotification(GroupCall groupCall) {
        if (notificationDispatcher == null || groupCall == null) {
            return;
        }
        try {
            List<UUID> recipients = participantRepository.findByGroupCallId(groupCall.getId()).stream()
                    .filter(participant -> participant.getState() == ParticipantState.INVITED
                            && participant.getJoinCount() == 0
                            && !participant.getUserId().equals(groupCall.getInitiatorId()))
                    .map(GroupCallParticipant::getUserId)
                    .distinct()
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }
            String initiatorName = userProfileRepository.findById(groupCall.getInitiatorId())
                    .map(UserProfile::getDisplayName)
                    .orElse("Ai đó");
            notificationDispatcher.dispatch(NotificationDispatchRequest.builder()
                    .type(NotificationType.MISSED_GROUP_CALL)
                    .targetType(NotificationTargetType.CALL)
                    .targetId(groupCall.getId())
                    .actorId(groupCall.getInitiatorId())
                    .explicitRecipientIds(recipients)
                    .conversationId(groupCall.getConversationId())
                    .metadata(Map.of(
                            "actorName", initiatorName,
                            "conversationName", "Cuộc gọi nhóm",
                            "groupCallId", groupCall.getId().toString(),
                            "callType", groupCall.getType() != null ? groupCall.getType().name() : ""
                    ))
                    .dedupKeyPrefix("group-call:" + groupCall.getId() + ":missed")
                    .recipientDirectlyAffected(true)
                    .build());
        } catch (Exception ex) {
            log.warn("[GroupCallService] Notification dispatch failed for missed groupCallId={}: {}",
                    groupCall.getId(),
                    ex.getMessage());
        }
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
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Cuộc gọi nhóm đã kết thúc.");
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
     * Heartbeat từ client để cập nhật thời gian hoạt động.
     */
    @Transactional
    public void pingGroupCall(UUID groupCallId, UUID userId) {
        // Dùng câu lệnh UPDATE trực tiếp để tránh Race Condition với việc leave
        participantRepository.updateLastJoinedAtIfJoined(groupCallId, userId, Instant.now());
    }

    /**
     * Thành viên rời cuộc gọi nhóm.
     * Cộng dồn thời gian tham gia, cập nhật state LEFT (không xóa hàng).
     */
    @Transactional
    public void leaveGroupCall(UUID groupCallId, UUID userId) {
        log.info("[GroupCallService] User {} leaving group call {}", userId, groupCallId);

        participantRepository.findByGroupCallIdAndUserId(groupCallId, userId)
                .ifPresent(participant -> {
                    // Cộng dồn thời gian tham gia
                    if (participant.getLastJoinedAt() != null) {
                        long durationSeconds = Instant.now().getEpochSecond() - participant.getLastJoinedAt().getEpochSecond();
                        participant.setTotalDuration(participant.getTotalDuration() + durationSeconds);
                    }
                    participant.setState(ParticipantState.LEFT);
                    participantRepository.saveAndFlush(participant); // Bắt buộc lưu ngay lập tức
                });

        // Lấy danh sách đang JOINED
        List<GroupCallParticipant> joinedParticipants = participantRepository
                .findByGroupCallIdAndState(groupCallId, ParticipantState.JOINED);
                
        // Lọc chắc chắn người vừa rời khỏi đã không còn bị đếm nhầm do cache
        long remainingJoined = joinedParticipants.stream()
                .filter(p -> !p.getUserId().equals(userId))
                .count();

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

        // Đánh dấu mọi participant còn JOINED thành LEFT để đảm bảo DB đồng nhất 100%
        participantRepository.findByGroupCallIdAndState(groupCallId, ParticipantState.JOINED)
                .forEach(p -> {
                    if (p.getLastJoinedAt() != null) {
                        long durationSeconds = Instant.now().getEpochSecond() - p.getLastJoinedAt().getEpochSecond();
                        p.setTotalDuration(p.getTotalDuration() + durationSeconds);
                    }
                    p.setState(ParticipantState.LEFT);
                    participantRepository.save(p);
                });

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

    /**
     * Dọn dẹp các cuộc gọi bị kẹt ở trạng thái RINGING hoặc ONGOING quá lâu
     * Kết hợp quét Heartbeat của các user trong ONGOING và RINGING call.
     */
    @Scheduled(fixedRate = 15000) // Chạy mỗi 15 giây 1 lần
    @Transactional
    public void cleanUpStaleGroupCalls() {
        Instant now = Instant.now();
        Instant twoMinutesAgo = now.minusSeconds(120);
        Instant pingTimeout = now.minusSeconds(15); // Nếu 15s không ping -> disconnect

        // Lấy tất cả cuộc gọi đang hoạt động (RINGING hoặc ONGOING)
        List<GroupCall> activeCalls = groupCallRepository.findAll().stream()
                .filter(c -> c.getStatus() == GroupCallStatus.RINGING || c.getStatus() == GroupCallStatus.ONGOING)
                .toList();

        for (GroupCall call : activeCalls) {
            boolean isRingingTooLong = call.getStatus() == GroupCallStatus.RINGING && call.getCreatedAt().isBefore(twoMinutesAgo);
            boolean hasActiveParticipant = false;

            // Kiểm tra heartbeat của những người đang JOINED
            List<GroupCallParticipant> joinedParticipants = participantRepository
                    .findByGroupCallIdAndState(call.getId(), ParticipantState.JOINED);

            for (GroupCallParticipant p : joinedParticipants) {
                // Nếu quá 15s không nhận được ping
                if (p.getLastJoinedAt() == null || p.getLastJoinedAt().isBefore(pingTimeout)) {
                    log.info("[GroupCallService-Cleanup] User {} timed out in call {}", p.getUserId(), call.getId());
                    if (p.getLastJoinedAt() != null) {
                        long durationSeconds = now.getEpochSecond() - p.getLastJoinedAt().getEpochSecond();
                        p.setTotalDuration(p.getTotalDuration() + durationSeconds);
                    }
                    p.setState(ParticipantState.LEFT);
                    participantRepository.save(p);
                } else {
                    hasActiveParticipant = true;
                }
            }

            // Nếu không còn ai active hoặc bị kẹt RINGING quá lâu -> ENDED
            if (!hasActiveParticipant || isRingingTooLong) {
                log.info("[GroupCallService-Cleanup] Call {} ended. Active participants: {}, Timeout RINGING: {}", 
                        call.getId(), hasActiveParticipant, isRingingTooLong);
                if (isRingingTooLong) {
                    safeDispatchMissedGroupCallNotification(call);
                }
                endGroupCall(call.getId());
            }
        }
    }
}
