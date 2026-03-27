package fit.iuh.cnm_project_be.room.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.dto.CreateConversationRequest;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final MessageUserStateRepository messageUserStateRepository;
    private final UserProfileRepository userProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId) {
        List<Conversation> conversations = conversationRepository.findAllByMemberId(userId);

        return conversations.stream()
                .map(conv -> mapToResponse(conv, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversationsCreatedByMe(UUID creatorId) {
        List<Conversation> conversations = conversationRepository.findByCreatorIdAndDeletedAtIsNull(creatorId);

        return conversations.stream()
                .map(conv -> mapToResponse(conv, creatorId))
                .toList();
    }

    @Transactional
    public ConversationResponse createConversation(UUID creatorId, CreateConversationRequest request) {
        Set<UUID> participantIds = normalizeParticipants(creatorId, request);

        if (request.getType() == ConversationType.PRIVATE) {
            UUID otherUserId = participantIds.stream()
                    .filter(participantId -> !participantId.equals(creatorId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Private conversation requires one other participant"));

            return conversationRepository.findPrivateConversationByParticipants(creatorId, otherUserId)
                    .map(existingConversation -> mapToResponse(existingConversation, creatorId))
                    .orElseGet(() -> createNewConversation(creatorId, request, participantIds));
        }

        return createNewConversation(creatorId, request, participantIds);
    }

    @Transactional
    public ConversationResponse addMember(UUID conversationId, UUID actorUserId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);

        ensureGroupConversation(conversation);
        ensureCanManageMembers(actorMember);
        ensureUserExists(targetUserId);

        if (conversationMemberRepository.existsByConversationIdAndUserId(conversationId, targetUserId)) {
            throw new BusinessException("User is already a member of the conversation");
        }

        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(targetUserId);
        member.setRole(MemberRole.MEMBER);
        member.setJoinedAt(Instant.now());
        conversationMemberRepository.save(member);

        broadcastConversationUpdates(conversationId);
        return mapToResponse(conversation, actorUserId);
    }

    @Transactional
    public ConversationResponse removeMember(UUID conversationId, UUID actorUserId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);
        ConversationMember targetMember = getMemberOrThrow(conversationId, targetUserId);

        ensureGroupConversation(conversation);
        ensureCanManageMembers(actorMember);
        if (actorUserId.equals(targetUserId)) {
            throw new BusinessException("Use leave conversation to remove yourself");
        }
        if (targetMember.getRole() == MemberRole.OWNER) {
            throw new BusinessException("Owner removal is not supported without ownership transfer");
        }
        if (actorMember.getRole() != MemberRole.OWNER && isPrivilegedRole(targetMember.getRole())) {
            throw new ForbiddenException("Only the owner can remove admins");
        }

        conversationMemberRepository.deleteByConversationIdAndUserId(conversationId, targetUserId);
        broadcastConversationUpdates(conversationId);
        return mapToResponse(conversation, actorUserId);
    }

    @Transactional
    public void leaveConversation(UUID conversationId, UUID actorUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);

        ensureGroupConversation(conversation);
        long memberCount = conversationMemberRepository.countByConversationId(conversationId);
        if (actorMember.getRole() == MemberRole.OWNER && memberCount > 1) {
            throw new BusinessException("Owner cannot leave until ownership transfer is implemented");
        }

        conversationMemberRepository.deleteByConversationIdAndUserId(conversationId, actorUserId);

        if (memberCount == 1) {
            conversation.setDeletedAt(Instant.now());
            conversationRepository.save(conversation);
            return;
        }

        broadcastConversationUpdates(conversationId);
    }

    private ConversationResponse mapToResponse(Conversation conv, UUID userId) {
        List<Message> lastMsgs = messageRepository.findVisibleMessages(conv.getId(), userId, null, null, PageRequest.of(0, 1));
        Message lastMsg = lastMsgs.isEmpty() ? null : lastMsgs.get(0);
        long unreadCount = messageUserStateRepository.countUnreadMessages(conv.getId(), userId);
        return ConversationResponse.builder()
                .id(conv.getId())
                .name(conv.getName())
                .type(String.valueOf(conv.getType()))
                .lastMessage(lastMsg != null ? lastMsg.getContent() : "")
                .lastMessageTime(lastMsg != null ? lastMsg.getCreatedAt() : conv.getCreatedAt())
                .unreadCount(unreadCount)
                .build();
    }

    private Set<UUID> normalizeParticipants(UUID creatorId, CreateConversationRequest request) {
        Set<UUID> participantIds = new LinkedHashSet<>();
        participantIds.add(creatorId);
        if (request.getParticipantIds() != null) {
            participantIds.addAll(request.getParticipantIds());
        }

        if (request.getType() == ConversationType.PRIVATE) {
            if (participantIds.size() != 2) {
                throw new BusinessException("Private conversation must contain exactly two participants");
            }
        } else {
            if (participantIds.size() < 2) {
                throw new BusinessException("Group conversation must contain at least two participants");
            }
            if (request.getName() == null || request.getName().isBlank()) {
                throw new BusinessException("Group conversation name is required");
            }
        }

        return participantIds;
    }

    private ConversationResponse createNewConversation(UUID creatorId, CreateConversationRequest request, Set<UUID> participantIds) {
        Conversation conversation = new Conversation();
        conversation.setCreatorId(creatorId);
        conversation.setType(request.getType());
        conversation.setName(request.getName() == null ? null : request.getName().trim());
        Conversation savedConversation = conversationRepository.save(conversation);

        for (UUID participantId : participantIds) {
            ConversationMember member = new ConversationMember();
            member.setConversationId(savedConversation.getId());
            member.setUserId(participantId);
            member.setRole(participantId.equals(creatorId) ? MemberRole.OWNER : MemberRole.MEMBER);
            conversationMemberRepository.save(member);
        }

        ConversationResponse response = mapToResponse(savedConversation, creatorId);
        participantIds.forEach(userId ->
                messagingTemplate.convertAndSend("/topic/users/" + userId + "/conversations",
                        RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED, response)));
        return response;
    }

    private void broadcastConversationUpdates(UUID conversationId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        conversationMemberRepository.findByConversationId(conversationId).forEach(member ->
                messagingTemplate.convertAndSend("/topic/users/" + member.getUserId() + "/conversations",
                        RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED,
                                mapToResponse(conversation, member.getUserId()))));
    }

    private Conversation getConversationOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> !conversation.isDeleted())
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    private ConversationMember getMemberOrThrow(UUID conversationId, UUID userId) {
        return conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("User does not belong to this conversation"));
    }

    private void ensureGroupConversation(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException("Only group conversations support membership changes");
        }
    }

    private void ensureCanManageMembers(ConversationMember actorMember) {
        if (!isPrivilegedRole(actorMember.getRole())) {
            throw new ForbiddenException("Only owners or admins can manage members");
        }
    }

    private boolean isPrivilegedRole(MemberRole role) {
        return role == MemberRole.OWNER || role == MemberRole.ADMIN;
    }

    private void ensureUserExists(UUID userId) {
        userProfileRepository.findById(userId)
                .filter(userProfile -> !userProfile.isDeleted())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }


}
