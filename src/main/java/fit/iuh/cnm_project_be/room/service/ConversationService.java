package fit.iuh.cnm_project_be.room.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.dto.ConversationMemberResponse;
import fit.iuh.cnm_project_be.room.dto.ConversationResponse;
import fit.iuh.cnm_project_be.room.dto.ConversationStatusPayload;
import fit.iuh.cnm_project_be.room.dto.ConversationBackgroundUploadResponse;
import fit.iuh.cnm_project_be.room.dto.CreateConversationRequest;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.ConversationBackgroundType;
import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import fit.iuh.cnm_project_be.storage.S3MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationUserSettingRepository conversationUserSettingRepository;
    private final MessageRepository messageRepository;
    private final MessageUserStateRepository messageUserStateRepository;
    private final UserProfileRepository userProfileRepository;
    private final S3MediaStorageService s3MediaStorageService;
    private final SimpMessagingTemplate messagingTemplate;
    private static final int MAX_CONVERSATION_NAME_LENGTH = 100;
    private static final int MAX_CONVERSATION_AVATAR_URL_LENGTH = 500;
    private static final int MAX_CUSTOM_CONVERSATION_NAME_LENGTH = 100;
    private static final int MAX_BACKGROUND_COLOR_LENGTH = 32;
    private static final int MAX_BACKGROUND_IMAGE_URL_LENGTH = 500;

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId, boolean archived) {
        List<Conversation> conversations = conversationRepository.findAllByMemberId(userId);
        return mapConversationResponses(conversations, userId, archived);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversationsCreatedByMe(UUID creatorId, boolean archived) {
        List<Conversation> conversations = conversationRepository.findByCreatorIdAndDeletedAtIsNull(creatorId);
        return mapConversationResponses(conversations, creatorId, archived);
    }

    @Transactional
    public ConversationResponse createConversation(UUID creatorId, CreateConversationRequest request) {
        Set<UUID> participantIds = normalizeParticipants(creatorId, request);
        participantIds.stream()
                .filter(participantId -> !participantId.equals(creatorId))
                .forEach(this::ensureUserExists);

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
    public ConversationResponse renameConversation(UUID conversationId, UUID actorUserId, String name) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);

        ensureGroupConversation(conversation);
        ensureCanManageMembers(actorMember);

        String normalizedName = normalizeConversationName(name);
        if (normalizedName.equals(conversation.getName())) {
            throw new BusinessException("Conversation name is unchanged");
        }

        conversation.setName(normalizedName);
        Conversation savedConversation = conversationRepository.save(conversation);
        log.debug("[GROUP RENAME SYNC] conversationId={} actorUserId={} nextName={}",
                conversationId,
                actorUserId,
                normalizedName);
        broadcastConversationUpdates(savedConversation.getId());
        ConversationResponse response = mapToResponse(savedConversation, actorUserId);
        logGroupLifecycleMembers("rename-group", response);
        return response;
    }

    @Transactional
    public ConversationResponse updateConversationAvatar(UUID conversationId, UUID actorUserId, String avatarUrl) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);

        ensureGroupConversation(conversation);
        ensureCanManageMembers(actorMember);

        String normalizedAvatarUrl = normalizeAvatarUrl(avatarUrl);
        if (normalizedAvatarUrl.equals(conversation.getAvatarUrl())) {
            throw new BusinessException("Conversation avatar is unchanged");
        }

        conversation.setAvatarUrl(normalizedAvatarUrl);
        Conversation savedConversation = conversationRepository.save(conversation);
        broadcastConversationUpdates(savedConversation.getId());
        ConversationResponse response = mapToResponse(savedConversation, actorUserId);
        logGroupLifecycleMembers("update-group-avatar", response);
        return response;
    }

    @Transactional
    public ConversationResponse transferOwnership(UUID conversationId, UUID actorUserId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);

        ensureGroupConversation(conversation);
        ensureOwner(actorMember);
        if (actorUserId.equals(targetUserId)) {
            throw new BusinessException("Ownership cannot be transferred to yourself");
        }
        ConversationMember targetMember = getMemberOrThrow(conversationId, targetUserId);
        ensureUserExists(targetUserId);
        if (targetMember.getRole() == MemberRole.OWNER) {
            throw new BusinessException("Target user is already the owner");
        }

        actorMember.setRole(MemberRole.ADMIN);
        targetMember.setRole(MemberRole.OWNER);
        conversationMemberRepository.save(actorMember);
        conversationMemberRepository.save(targetMember);

        broadcastConversationUpdates(conversationId);
        ConversationResponse response = mapToResponse(conversation, actorUserId);
        logGroupLifecycleMembers("transfer-ownership", response);
        return response;
    }

    @Transactional
    public ConversationResponse promoteToAdmin(UUID conversationId, UUID actorUserId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);
        ConversationMember targetMember = getMemberOrThrow(conversationId, targetUserId);

        ensureGroupConversation(conversation);
        ensureOwner(actorMember);
        ensureUserExists(targetUserId);
        if (targetMember.getRole() != MemberRole.MEMBER) {
            throw new BusinessException("Only members can be promoted to admin");
        }

        targetMember.setRole(MemberRole.ADMIN);
        conversationMemberRepository.save(targetMember);
        broadcastConversationUpdates(conversationId);
        ConversationResponse response = mapToResponse(conversation, actorUserId);
        logGroupLifecycleMembers("promote-admin", response);
        return response;
    }

    @Transactional
    public ConversationResponse demoteAdmin(UUID conversationId, UUID actorUserId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);
        ConversationMember targetMember = getMemberOrThrow(conversationId, targetUserId);

        ensureGroupConversation(conversation);
        ensureOwner(actorMember);
        ensureUserExists(targetUserId);
        if (targetMember.getRole() == MemberRole.OWNER) {
            throw new BusinessException("Owner cannot be demoted through admin management");
        }
        if (targetMember.getRole() != MemberRole.ADMIN) {
            throw new BusinessException("Only admins can be demoted to member");
        }

        targetMember.setRole(MemberRole.MEMBER);
        conversationMemberRepository.save(targetMember);
        broadcastConversationUpdates(conversationId);
        ConversationResponse response = mapToResponse(conversation, actorUserId);
        logGroupLifecycleMembers("demote-admin", response);
        return response;
    }

    @Transactional
    public void closeConversation(UUID conversationId, UUID actorUserId) {
        Conversation conversation = getConversationIncludingDeletedOrThrow(conversationId);
        if (conversation.isDeleted()) {
            throw new BusinessException("Conversation has already been deleted");
        }

        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);
        ensureGroupConversation(conversation);
        ensureOwner(actorMember);

        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
        List<ConversationMember> removedMembers = members.stream()
                .filter(member -> !member.getUserId().equals(actorUserId))
                .toList();
        log.info("[GROUP DISBAND] conversationId={} ownerId={} memberCount={}",
                conversationId,
                actorUserId,
                members.size());
        removedMembers.forEach(member -> {
            log.info("[GROUP REMOVE MEMBERS] conversationId={} removedUserId={} role={}",
                    conversationId,
                    member.getUserId(),
                    member.getRole());
            conversationMemberRepository.deleteByConversationIdAndUserId(conversationId, member.getUserId());
        });
        softDeleteConversation(conversation);
        broadcastConversationDeleted(conversationId, members, true);
    }

    @Transactional
    public void updateMutePreference(UUID conversationId, UUID actorUserId, boolean muted) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);
        updateUserSetting(
                conversation.getId(),
                actorUserId,
                muted,
                ConversationUserSetting::getMutedAt,
                ConversationUserSetting::setMutedAt);
    }

    @Transactional
    public void updateArchivePreference(UUID conversationId, UUID actorUserId, boolean archived) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);
        updateUserSetting(
                conversation.getId(),
                actorUserId,
                archived,
                ConversationUserSetting::getArchivedAt,
                ConversationUserSetting::setArchivedAt);
    }

    @Transactional
    public void updatePinPreference(UUID conversationId, UUID actorUserId, boolean pinned) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);
        updateUserSetting(
                conversation.getId(),
                actorUserId,
                pinned,
                ConversationUserSetting::getPinnedAt,
                ConversationUserSetting::setPinnedAt);
    }

    @Transactional
    public void updateNotificationLevel(UUID conversationId, UUID actorUserId,
            ConversationNotificationLevel notificationLevel) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);

        ConversationNotificationLevel normalizedLevel = normalizeNotificationLevel(notificationLevel);
        ConversationUserSetting setting = conversationUserSettingRepository
                .findByConversationIdAndUserId(conversationId, actorUserId)
                .orElse(null);
        ConversationNotificationLevel currentLevel = resolveNotificationLevel(setting);

        if (currentLevel == normalizedLevel) {
            return;
        }

        if (setting == null) {
            setting = new ConversationUserSetting();
            setting.setConversationId(conversation.getId());
            setting.setUserId(actorUserId);
        }

        setting.setNotificationLevel(normalizedLevel == ConversationNotificationLevel.ALL ? null : normalizedLevel);
        conversationUserSettingRepository.save(setting);
    }

    @Transactional
    public void updateCustomName(UUID conversationId, UUID actorUserId, String customName) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);

        String normalizedCustomName = normalizeCustomName(customName);
        ConversationUserSetting setting = conversationUserSettingRepository
                .findByConversationIdAndUserId(conversationId, actorUserId)
                .orElse(null);
        String currentCustomName = setting != null ? setting.getCustomName() : null;

        if (Objects.equals(currentCustomName, normalizedCustomName)) {
            return;
        }

        if (setting == null && normalizedCustomName == null) {
            return;
        }

        if (setting == null) {
            setting = new ConversationUserSetting();
            setting.setConversationId(conversation.getId());
            setting.setUserId(actorUserId);
        }

        setting.setCustomName(normalizedCustomName);
        conversationUserSettingRepository.save(setting);
    }

    @Transactional
    public void updateBackground(
            UUID conversationId,
            UUID actorUserId,
            ConversationBackgroundType backgroundType,
            String backgroundColor,
            String backgroundImageUrl) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);

        ConversationBackgroundType normalizedType = normalizeBackgroundType(backgroundType);
        String normalizedColor = normalizeBackgroundColor(backgroundColor, normalizedType);
        String normalizedImageUrl = normalizeBackgroundImageUrl(backgroundImageUrl, normalizedType);

        if (Objects.equals(resolveConversationBackgroundType(conversation), normalizedType)
                && Objects.equals(conversation.getBackgroundColor(), normalizedColor)
                && Objects.equals(conversation.getBackgroundImageUrl(), normalizedImageUrl)) {
            return;
        }

        conversation.setBackgroundType(normalizedType == ConversationBackgroundType.DEFAULT ? null : normalizedType);
        conversation.setBackgroundColor(normalizedColor);
        conversation.setBackgroundImageUrl(normalizedImageUrl);
        Conversation savedConversation = conversationRepository.save(conversation);
        broadcastConversationUpdates(savedConversation.getId());
    }

    @Transactional
    public ConversationBackgroundUploadResponse uploadBackgroundImage(UUID conversationId, UUID actorUserId, MultipartFile file) {
        getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);

        UploadAttachmentResponse upload = s3MediaStorageService.upload(actorUserId, file);
        return ConversationBackgroundUploadResponse.builder()
                .url(upload.getUrl())
                .storageKey(upload.getStorageKey())
                .fileName(upload.getFileName())
                .contentType(upload.getContentType())
                .fileSize(upload.getFileSize())
                .build();
    }

    @Transactional
    public ConversationResponse addMember(UUID conversationId, UUID actorUserId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationMember actorMember = getMemberOrThrow(conversationId, actorUserId);

        ensureGroupConversation(conversation);
        ensureCanAddMembers(actorMember);
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
        ConversationResponse response = mapToResponse(conversation, actorUserId);
        logGroupLifecycleMembers("add-member", response);
        return response;
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
        ConversationResponse response = mapToResponse(conversation, actorUserId);
        logGroupLifecycleMembers("remove-member", response);
        return response;
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
            softDeleteConversation(conversation);
            return;
        }

        broadcastConversationUpdates(conversationId);
    }

    private ConversationResponse mapToResponse(Conversation conv, UUID userId) {
        return mapToResponse(
                conv,
                userId,
                conversationUserSettingRepository.findByConversationIdAndUserId(conv.getId(), userId).orElse(null));
    }

    private ConversationResponse mapToResponse(Conversation conv, UUID userId, ConversationUserSetting setting) {
        List<Message> lastMsgs = messageRepository.findVisibleMessages(conv.getId(), userId, PageRequest.of(0, 1));
        Message lastMsg = lastMsgs.isEmpty() ? null : lastMsgs.get(0);
        long unreadCount = messageUserStateRepository.countUnreadMessages(conv.getId(), userId);
        List<ConversationMemberResponse> groupMembers = buildGroupMembers(conv);
        PrivatePeerInfo privatePeerInfo = resolvePrivatePeerInfo(conv, userId);
        String displayName = resolveDisplayName(conv, setting, privatePeerInfo);
        String avatarUrl = resolveAvatarUrl(conv, privatePeerInfo);
        ConversationResponse response = ConversationResponse.builder()
                .id(conv.getId())
                .name(conv.getName())
                .avatarUrl(avatarUrl)
                .type(String.valueOf(conv.getType()))
                .lastMessage(lastMsg != null ? lastMsg.getContent() : "")
                .lastMessageTime(lastMsg != null ? lastMsg.getCreatedAt() : conv.getCreatedAt())
                .unreadCount(unreadCount)
                .muted(setting != null && setting.getMutedAt() != null)
                .archived(setting != null && setting.getArchivedAt() != null)
                .pinned(setting != null && setting.getPinnedAt() != null)
                .notificationLevel(resolveNotificationLevel(setting))
                .customName(setting != null ? setting.getCustomName() : null)
                .backgroundType(resolveConversationBackgroundType(conv))
                .backgroundColor(conv.getBackgroundColor())
                .backgroundImageUrl(conv.getBackgroundImageUrl())
                .displayName(displayName)

                .peerUserId(privatePeerInfo.userId())
                .peerDisplayName(privatePeerInfo.displayName())
                .peerAvatarUrl(privatePeerInfo.avatarUrl())
                .members(groupMembers)

                .build();

        if (conv.getType() == ConversationType.GROUP) {
            log.debug("[BE GROUP RESPONSE MEMBERS] conversationId={} userId={} memberCount={} members={}",
                    conv.getId(),
                    userId,
                    groupMembers.size(),
                    groupMembers.stream()
                            .map(member -> member.getUserId() + ":" + member.getRole())
                            .toList());
        }

        return response;
    }

    private List<ConversationResponse> mapConversationResponses(List<Conversation> conversations, UUID userId,
            boolean archived) {
        Map<UUID, ConversationUserSetting> settingsByConversationId = getSettingsByConversationId(conversations,
                userId);

        return conversations.stream()
                .filter(conversation -> {
                    ConversationUserSetting setting = settingsByConversationId.get(conversation.getId());
                    boolean archivedState = setting != null && setting.getArchivedAt() != null;
                    return archivedState == archived;
                })
                .map(conversation -> new ConversationWithPreference(
                        mapToResponse(conversation, userId, settingsByConversationId.get(conversation.getId())),
                        settingsByConversationId.get(conversation.getId())))
                .sorted(Comparator
                        .comparing(ConversationWithPreference::isPinned)
                        .reversed()
                        .thenComparing(item -> item.response().getLastMessageTime(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ConversationWithPreference::response)
                .toList();
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
            long selectedParticipantCount = participantIds.stream()
                    .filter(participantId -> !participantId.equals(creatorId))
                    .count();
            log.debug("[GROUP VALIDATION] creatorId={} selectedParticipantCount={} participantCount={}",
                    creatorId,
                    selectedParticipantCount,
                    participantIds.size());
            if (selectedParticipantCount < 2) {
                throw new BusinessException("Group conversation must contain at least two selected members");
            }
            normalizeConversationName(request.getName());
        }

        return participantIds;
    }

    private ConversationResponse createNewConversation(UUID creatorId, CreateConversationRequest request,
            Set<UUID> participantIds) {
        Conversation conversation = new Conversation();
        conversation.setCreatorId(creatorId);
        conversation.setType(request.getType());
        conversation.setName(
                request.getType() == ConversationType.GROUP ? normalizeConversationName(request.getName()) : null);
        Conversation savedConversation = conversationRepository.save(conversation);

        for (UUID participantId : participantIds) {
            ConversationMember member = new ConversationMember();
            member.setConversationId(savedConversation.getId());
            member.setUserId(participantId);
            member.setRole(participantId.equals(creatorId) ? MemberRole.OWNER : MemberRole.MEMBER);
            conversationMemberRepository.save(member);
        }

        ConversationResponse creatorResponse = mapToResponse(savedConversation, creatorId);
        logGroupLifecycleMembers("create-group", creatorResponse);
        participantIds.forEach(userId -> messagingTemplate.convertAndSend(
                "/topic/users/" + userId + "/conversations",
                RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED, mapToResponse(savedConversation, userId))));
        return creatorResponse;
    }

    private void broadcastConversationUpdates(UUID conversationId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ConversationResponse sharedConversationPayload = mapToResponse(conversation, null);
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + conversationId,
                RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED, sharedConversationPayload));

        conversationMemberRepository.findByConversationId(conversationId).forEach(member -> {
            ConversationUserSetting setting = findConversationUserSetting(conversationId, member.getUserId());
            if (!shouldDeliverConversationRefresh(setting)) {
                return;
            }

            ConversationResponse response = mapToResponse(conversation, member.getUserId(), setting);
            log.debug("[GROUP RENAME SYNC] event=CONVERSATION_UPDATED conversationId={} recipientUserId={} displayName={} memberCount={}",
                    conversationId,
                    member.getUserId(),
                    response.getDisplayName(),
                    response.getMembers() != null ? response.getMembers().size() : 0);
            messagingTemplate.convertAndSend("/topic/users/" + member.getUserId() + "/conversations",
                    RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED, response));
        });
    }

    private void broadcastConversationDeleted(UUID conversationId, List<ConversationMember> members, boolean isDisbanded) {
        ConversationStatusPayload payload = new ConversationStatusPayload(conversationId, "DELETED", isDisbanded);
        members.forEach(
                member -> messagingTemplate.convertAndSend("/topic/users/" + member.getUserId() + "/conversations",
                        RealtimeEvent.of(RealtimeEventType.CONVERSATION_UPDATED, payload)));
    }

    private Conversation getConversationOrThrow(UUID conversationId) {
        Conversation conversation = getConversationIncludingDeletedOrThrow(conversationId);
        if (conversation.isDeleted()) {
            throw new NotFoundException("Conversation not found");
        }
        return conversation;
    }

    private Conversation getConversationIncludingDeletedOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    private void softDeleteConversation(Conversation conversation) {
        conversation.setDeletedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    private Map<UUID, ConversationUserSetting> getSettingsByConversationId(List<Conversation> conversations,
            UUID userId) {
        if (conversations.isEmpty()) {
            return Map.of();
        }

        List<UUID> conversationIds = conversations.stream()
                .map(Conversation::getId)
                .toList();

        Map<UUID, ConversationUserSetting> settingsByConversationId = new HashMap<>();
        conversationUserSettingRepository.findByUserIdAndConversationIdIn(userId, conversationIds)
                .forEach(setting -> settingsByConversationId.put(setting.getConversationId(), setting));
        return settingsByConversationId;
    }

    private ConversationUserSetting findConversationUserSetting(UUID conversationId, UUID userId) {
        return conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);
    }

    private ConversationMember getMemberOrThrow(UUID conversationId, UUID userId) {
        return conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("User does not belong to this conversation"));
    }

    private void ensureConversationMember(UUID conversationId, UUID userId) {
        getMemberOrThrow(conversationId, userId);
    }

    private void ensureGroupConversation(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException("Only group conversations support membership changes");
        }
    }

    private void ensureCanManageMembers(ConversationMember actorMember) {
        log.debug("[GROUP ROLE CHECK] action=manage actorUserId={} actorRole={}",
                actorMember.getUserId(),
                actorMember.getRole());
        if (!isPrivilegedRole(actorMember.getRole())) {
            throw new ForbiddenException("Only owners or admins can manage members");
        }
    }

    private void ensureCanAddMembers(ConversationMember actorMember) {
        log.debug("[GROUP ROLE CHECK] action=add-member actorUserId={} actorRole={}",
                actorMember.getUserId(),
                actorMember.getRole());
        if (actorMember.getRole() == null || actorMember.getRole() == MemberRole.GUEST) {
            throw new ForbiddenException("Only conversation members can add members");
        }
    }

    private void ensureOwner(ConversationMember actorMember) {
        if (actorMember.getRole() != MemberRole.OWNER) {
            throw new ForbiddenException("Only the owner can manage room roles");
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

    private String normalizeConversationName(String name) {
        if (name == null) {
            throw new BusinessException("Conversation name is required");
        }

        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new BusinessException("Conversation name must not be blank");
        }
        if (normalizedName.length() > MAX_CONVERSATION_NAME_LENGTH) {
            throw new BusinessException("Conversation name must be less than 100 characters");
        }

        return normalizedName;
    }

    private String normalizeAvatarUrl(String avatarUrl) {
        if (avatarUrl == null) {
            throw new BusinessException("Conversation avatar URL is required");
        }

        String normalizedAvatarUrl = avatarUrl.trim();
        if (normalizedAvatarUrl.isEmpty()) {
            throw new BusinessException("Conversation avatar URL must not be blank");
        }
        if (normalizedAvatarUrl.length() > MAX_CONVERSATION_AVATAR_URL_LENGTH) {
            throw new BusinessException("Conversation avatar URL must be less than 500 characters");
        }

        return normalizedAvatarUrl;
    }

    private ConversationNotificationLevel normalizeNotificationLevel(ConversationNotificationLevel notificationLevel) {
        if (notificationLevel == null) {
            throw new BusinessException("Notification level is required");
        }
        return notificationLevel;
    }

    private ConversationNotificationLevel resolveNotificationLevel(ConversationUserSetting setting) {
        return setting != null && setting.getNotificationLevel() != null
                ? setting.getNotificationLevel()
                : ConversationNotificationLevel.ALL;
    }

    private boolean shouldDeliverConversationRefresh(ConversationUserSetting setting) {
        ConversationNotificationLevel notificationLevel = resolveNotificationLevel(setting);
        log.debug("[BE UNREAD VS NOTIFY POLICY] scope=conversation-refresh notificationLevel={} deliver=true reason=conversation-state-sync",
                notificationLevel);
        return true;
    }

    private List<ConversationMemberResponse> buildGroupMembers(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            return null;
        }

        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
        Map<UUID, UserProfile> profilesByUserId = loadUserProfilesByUserId(members.stream()
                .map(ConversationMember::getUserId)
                .toList());
        List<ConversationMemberResponse> memberResponses = members.stream()
                .map(member -> mapConversationMember(member, profilesByUserId.get(member.getUserId())))
                .toList();

        log.debug("[GROUP MEMBER MAP] conversationId={} source=builder memberCount={} members={}",
                conversation.getId(),
                memberResponses.size(),
                memberResponses.stream()
                        .map(member -> member.getUserId() + ":" + member.getRole())
                        .toList());

        return memberResponses;
    }

    private String normalizeCustomName(String customName) {
        if (customName == null) {
            return null;
        }

        String normalizedCustomName = customName.trim();
        if (normalizedCustomName.isEmpty()) {
            return null;
        }
        if (normalizedCustomName.length() > MAX_CUSTOM_CONVERSATION_NAME_LENGTH) {
            throw new BusinessException("Custom conversation name must be less than 100 characters");
        }

        return normalizedCustomName;
    }

    private ConversationBackgroundType normalizeBackgroundType(ConversationBackgroundType backgroundType) {
        if (backgroundType == null) {
            throw new BusinessException("Background type is required");
        }
        return backgroundType;
    }

    private ConversationBackgroundType resolveConversationBackgroundType(Conversation conversation) {
        return conversation.getBackgroundType() != null
                ? conversation.getBackgroundType()
                : ConversationBackgroundType.DEFAULT;
    }

    private String normalizeBackgroundColor(String backgroundColor, ConversationBackgroundType backgroundType) {
        if (backgroundType != ConversationBackgroundType.COLOR) {
            return null;
        }
        if (backgroundColor == null) {
            throw new BusinessException("Background color is required for color background");
        }
        String normalized = backgroundColor.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("Background color must not be blank");
        }
        if (normalized.length() > MAX_BACKGROUND_COLOR_LENGTH) {
            throw new BusinessException("Background color must be less than 32 characters");
        }
        return normalized;
    }

    private String normalizeBackgroundImageUrl(String backgroundImageUrl, ConversationBackgroundType backgroundType) {
        if (backgroundType != ConversationBackgroundType.IMAGE) {
            return null;
        }
        if (backgroundImageUrl == null) {
            throw new BusinessException("Background image URL is required for image background");
        }
        String normalized = backgroundImageUrl.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("Background image URL must not be blank");
        }
        if (normalized.length() > MAX_BACKGROUND_IMAGE_URL_LENGTH) {
            throw new BusinessException("Background image URL must be less than 500 characters");
        }
        return normalized;
    }

    private String resolveDisplayName(
            Conversation conversation,
            ConversationUserSetting setting,
            PrivatePeerInfo privatePeerInfo) {
        if (setting != null && setting.getCustomName() != null) {
            return setting.getCustomName();
        }

        if (conversation.getType() == ConversationType.PRIVATE) {
            return privatePeerInfo.displayName() != null
                    ? privatePeerInfo.displayName()
                    : conversation.getName();
        }

        return conversation.getName();
    }

    private String resolveAvatarUrl(Conversation conversation, PrivatePeerInfo privatePeerInfo) {
        if (conversation.getType() != ConversationType.PRIVATE) {
            return conversation.getAvatarUrl();
        }

        return privatePeerInfo.avatarUrl() != null
                ? privatePeerInfo.avatarUrl()
                : conversation.getAvatarUrl();
    }

    private PrivatePeerInfo resolvePrivatePeerInfo(Conversation conversation, UUID userId) {
        if (conversation.getType() != ConversationType.PRIVATE || userId == null) {
            return PrivatePeerInfo.empty();
        }

        return conversationMemberRepository.findPartnerUserId(conversation.getId(), userId)
                .flatMap(userProfileRepository::findById)
                .filter(userProfile -> !userProfile.isDeleted())
                .map(userProfile -> new PrivatePeerInfo(
                        userProfile.getUserId(),
                        resolveUserDisplayName(userProfile),
                        userProfile.getAvatarUrl()))
                .orElseGet(PrivatePeerInfo::empty);
    }

    private ConversationMemberResponse mapConversationMember(ConversationMember member, UserProfile userProfile) {
        return ConversationMemberResponse.builder()
                .userId(member.getUserId())
                .username(userProfile != null ? normalizeNullableText(userProfile.getUsername()) : null)
                .displayName(resolveUserDisplayName(userProfile, member.getUserId()))
                .avatarUrl(userProfile != null ? normalizeNullableText(userProfile.getAvatarUrl()) : null)
                .role(member.getRole())
                .build();
    }

    private String resolveUserDisplayName(UserProfile userProfile) {
        return resolveUserDisplayName(userProfile, null);
    }

    private String resolveUserDisplayName(UserProfile userProfile, UUID fallbackUserId) {
        if (userProfile == null) {
            return fallbackUserId != null ? fallbackUserId.toString() : null;
        }

        String displayName = userProfile.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }

        String fullName = resolveFullName(userProfile);
        if (fullName != null) {
            return fullName;
        }

        String username = userProfile.getUsername();
        if (username != null && !username.isBlank()) {
            return username;
        }

        UUID userId = userProfile.getUserId() != null ? userProfile.getUserId() : fallbackUserId;
        return userId != null ? userId.toString() : null;
    }

    private String resolveFullName(UserProfile userProfile) {
        String firstName = userProfile.getFirstName();
        String lastName = userProfile.getLastName();

        String fullName = String.join(" ",
                firstName != null ? firstName.trim() : "",
                lastName != null ? lastName.trim() : "").trim();
        return fullName.isEmpty() ? null : fullName;
    }

    private Map<UUID, UserProfile> loadUserProfilesByUserId(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UserProfile> profilesByUserId = new HashMap<>();
        userProfileRepository.findAllById(userIds).stream()
                .filter(userProfile -> !userProfile.isDeleted())
                .forEach(userProfile -> profilesByUserId.put(userProfile.getUserId(), userProfile));
        return profilesByUserId;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private void logGroupLifecycleMembers(String action, ConversationResponse response) {
        if (response == null || response.getMembers() == null) {
            return;
        }

        log.debug("[BE GROUP LIFECYCLE MEMBERS] action={} conversationId={} memberCount={} members={}",
                action,
                response.getId(),
                response.getMembers().size(),
                response.getMembers().stream()
                        .map(member -> member.getUserId() + ":" + member.getRole())
                        .toList());
    }

    private void updateUserSetting(
            UUID conversationId,
            UUID userId,
            boolean enabled,
            java.util.function.Function<ConversationUserSetting, Instant> getter,
            java.util.function.BiConsumer<ConversationUserSetting, Instant> setter) {

        ConversationUserSetting setting = conversationUserSettingRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);

        if (setting == null && !enabled) {
            return;
        }

        if (setting == null) {
            setting = new ConversationUserSetting();
            setting.setConversationId(conversationId);
            setting.setUserId(userId);
        }

        Instant currentValue = getter.apply(setting);
        if (enabled && currentValue != null) {
            return;
        }
        if (!enabled && currentValue == null) {
            return;
        }

        setter.accept(setting, enabled ? Instant.now() : null);
        conversationUserSettingRepository.save(setting);
    }

    private record ConversationWithPreference(ConversationResponse response, ConversationUserSetting setting) {
        private boolean isPinned() {
            return setting != null && setting.getPinnedAt() != null;
        }
    }

    private record PrivatePeerInfo(UUID userId, String displayName, String avatarUrl) {
        private static PrivatePeerInfo empty() {
            return new PrivatePeerInfo(null, null, null);
        }
    }

}
