package fit.iuh.cnm_project_be.room.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.message.entity.Message;
import fit.iuh.cnm_project_be.message.entity.MessageStatus;
import fit.iuh.cnm_project_be.message.entity.MessageUserState;
import fit.iuh.cnm_project_be.message.dto.MessageResponse;
import fit.iuh.cnm_project_be.message.dto.UploadAttachmentResponse;
import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.repository.MessageRepository;
import fit.iuh.cnm_project_be.message.repository.MessageStatusRepository;
import fit.iuh.cnm_project_be.message.repository.MessageUserStateRepository;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEvent;
import fit.iuh.cnm_project_be.realtime.dto.RealtimeEventType;
import fit.iuh.cnm_project_be.room.dto.ConversationMemberResponse;
import fit.iuh.cnm_project_be.room.dto.ConversationGroupLabelPresetResponse;
import fit.iuh.cnm_project_be.room.dto.ConversationGroupLabelResponse;
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
import fit.iuh.cnm_project_be.room.enums.GroupConversationLabel;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final MessageStatusRepository messageStatusRepository;
    private final MessageUserStateRepository messageUserStateRepository;
    private final UserProfileRepository userProfileRepository;
    private final S3MediaStorageService s3MediaStorageService;
    private final NotificationDispatcher notificationDispatcher;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private static final String GROUP_SYSTEM_PREFIX = "[[GROUP_SYSTEM]]";
    private static final int MAX_CONVERSATION_NAME_LENGTH = 100;
    private static final int MAX_CONVERSATION_AVATAR_URL_LENGTH = 500;
    private static final int MAX_CUSTOM_CONVERSATION_NAME_LENGTH = 100;
    private static final int MAX_MEMBER_NICKNAME_LENGTH = 100;
    private static final int MAX_BACKGROUND_COLOR_LENGTH = 32;
    private static final int MAX_BACKGROUND_IMAGE_URL_LENGTH = 500;

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId, boolean archived) {
        return getMyConversations(userId, archived, null);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId, boolean archived, String groupLabelCode) {
        GroupConversationLabel groupLabelFilter = normalizeGroupConversationLabel(groupLabelCode);
        List<Conversation> conversations = conversationRepository.findAllByMemberId(userId);
        return mapConversationResponses(conversations, userId, archived, groupLabelFilter);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversationsCreatedByMe(UUID creatorId, boolean archived) {
        List<Conversation> conversations = conversationRepository.findByCreatorIdAndDeletedAtIsNull(creatorId);
        return mapConversationResponses(conversations, creatorId, archived, null);
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
        createAndBroadcastGroupSystemMessage(savedConversation, actorUserId, "group_renamed", null,
                Map.of("name", normalizedName));
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
        createAndBroadcastGroupSystemMessage(savedConversation, actorUserId, "group_avatar_changed", null,
                Map.of("avatarUrl", normalizedAvatarUrl));
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

        createAndBroadcastGroupSystemMessage(conversation, actorUserId, "group_owner_transferred", targetUserId,
                Map.of());
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
        createAndBroadcastGroupSystemMessage(conversation, actorUserId, "group_admin_promoted", targetUserId,
                Map.of());
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
        createAndBroadcastGroupSystemMessage(conversation, actorUserId, "group_admin_demoted", targetUserId,
                Map.of());
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
        List<UUID> disbandRecipients = removedMembers.stream()
                .map(ConversationMember::getUserId)
                .toList();
        log.info("[GROUP DISBAND] conversationId={} ownerId={} memberCount={}",
                conversationId,
                actorUserId,
                members.size());
        safeDispatchGroupDisbandedNotification(conversation, actorUserId, disbandRecipients);
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
        ConversationUserSetting setting = conversationUserSettingRepository
                .findByConversationIdAndUserId(conversation.getId(), actorUserId)
                .orElse(null);

        if (setting == null && !muted) {
            return;
        }
        if (setting == null) {
            setting = new ConversationUserSetting();
            setting.setConversationId(conversation.getId());
            setting.setUserId(actorUserId);
        }

        if (muted) {
            Instant now = Instant.now();
            setting.setMutedAt(now);
            setting.setLastMutedAt(now);
        } else {
            setting.setMutedAt(null);
            setting.setMutedUntil(null);
            if (setting.getNotificationLevel() == ConversationNotificationLevel.NONE) {
                setting.setNotificationLevel(ConversationNotificationLevel.ALL);
            }
        }
        conversationUserSettingRepository.save(setting);
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

        setting.setNotificationLevel(normalizedLevel);
        if (normalizedLevel == ConversationNotificationLevel.NONE) {
            Instant now = Instant.now();
            setting.setMutedAt(now);
            setting.setLastMutedAt(now);
        } else if (normalizedLevel == ConversationNotificationLevel.ALL) {
            setting.setMutedAt(null);
            setting.setMutedUntil(null);
        }
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

    @Transactional(readOnly = true)
    public List<ConversationGroupLabelPresetResponse> getGroupLabelPresets() {
        return Arrays.stream(GroupConversationLabel.values())
                .map(label -> ConversationGroupLabelPresetResponse.builder()
                        .code(label.name())
                        .displayName(label.getDisplayName())
                        .color(label.getColor())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationGroupLabelResponse getMyConversationGroupLabel(UUID conversationId, UUID actorUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);
        ensureGroupConversationForLabel(conversation);

        ConversationUserSetting setting = findConversationUserSetting(conversationId, actorUserId);
        return mapConversationGroupLabelResponse(conversationId, actorUserId, setting);
    }

    @Transactional
    public ConversationGroupLabelResponse updateMyConversationGroupLabel(
            UUID conversationId,
            UUID actorUserId,
            String groupLabelCode) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);
        ensureGroupConversationForLabel(conversation);

        GroupConversationLabel normalizedLabel = normalizeGroupConversationLabel(groupLabelCode);
        ConversationUserSetting setting = findConversationUserSetting(conversationId, actorUserId);

        if (setting == null && normalizedLabel == null) {
            return mapConversationGroupLabelResponse(conversationId, actorUserId, null);
        }

        if (setting == null) {
            setting = new ConversationUserSetting();
            setting.setConversationId(conversationId);
            setting.setUserId(actorUserId);
        }

        if (Objects.equals(setting.getGroupLabel(), normalizedLabel)) {
            return mapConversationGroupLabelResponse(conversationId, actorUserId, setting);
        }

        setting.setGroupLabel(normalizedLabel);
        ConversationUserSetting savedSetting = conversationUserSettingRepository.save(setting);
        return mapConversationGroupLabelResponse(conversationId, actorUserId, savedSetting);
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
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("backgroundType", normalizedType.name());
        if (normalizedColor != null) {
            metadata.put("backgroundColor", normalizedColor);
        }
        if (normalizedImageUrl != null) {
            metadata.put("backgroundImageUrl", normalizedImageUrl);
        }
        createAndBroadcastGroupSystemMessage(savedConversation, actorUserId, "group_background_changed", null,
                metadata);
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

        createAndBroadcastGroupSystemMessage(conversation, actorUserId, "group_member_added", targetUserId,
                Map.of());
        broadcastConversationUpdates(conversationId);
        ConversationResponse response = mapToResponse(conversation, actorUserId);
        logGroupLifecycleMembers("add-member", response);
        return response;
    }

    @Transactional
    public ConversationResponse updateMemberNickname(
            UUID conversationId,
            UUID actorUserId,
            UUID targetUserId,
            String nickname) {
        Conversation conversation = getConversationOrThrow(conversationId);
        getMemberOrThrow(conversationId, actorUserId);
        ConversationMember targetMember = getMemberOrThrow(conversationId, targetUserId);

        ensureGroupConversation(conversation);

        String normalizedNickname = normalizeMemberNickname(nickname);
        String currentNickname = normalizeNullableText(targetMember.getNickname());

        if (Objects.equals(currentNickname, normalizedNickname)) {
            return mapToResponse(conversation, actorUserId);
        }

        targetMember.setNickname(normalizedNickname);
        conversationMemberRepository.save(targetMember);

        Map<String, Object> metadata = new HashMap<>();
        if (normalizedNickname != null) {
            metadata.put("nickname", normalizedNickname);
        }
        createAndBroadcastGroupSystemMessage(
                conversation,
                actorUserId,
                "group_nickname_changed",
                targetUserId,
                metadata);
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

        createAndBroadcastGroupSystemMessage(conversation, actorUserId, "group_member_removed", targetUserId,
                Map.of());
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

        createAndBroadcastGroupSystemMessage(conversation, actorUserId, "group_left", actorUserId, Map.of());
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
        GroupConversationLabel groupLabel = conv.getType() == ConversationType.GROUP && setting != null
                ? setting.getGroupLabel()
                : null;
        ConversationResponse response = ConversationResponse.builder()
                .id(conv.getId())
                .name(conv.getName())
                .avatarUrl(avatarUrl)
                .type(String.valueOf(conv.getType()))
                .lastMessage(lastMsg != null ? resolveConversationPreviewText(lastMsg) : "")
                .lastMessageTime(lastMsg != null ? lastMsg.getCreatedAt() : conv.getCreatedAt())
                .unreadCount(unreadCount)
                .muted(isMuted(setting))
                .archived(setting != null && setting.getArchivedAt() != null)
                .pinned(setting != null && setting.getPinnedAt() != null)
                .notificationLevel(resolveNotificationLevel(setting))
                .customName(setting != null ? setting.getCustomName() : null)
                .backgroundType(resolveConversationBackgroundType(conv))
                .backgroundColor(conv.getBackgroundColor())
                .backgroundImageUrl(conv.getBackgroundImageUrl())
                .groupLabel(groupLabel != null ? groupLabel.name() : null)
                .groupLabelDisplayName(groupLabel != null ? groupLabel.getDisplayName() : null)
                .groupLabelColor(groupLabel != null ? groupLabel.getColor() : null)
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

    private List<ConversationResponse> mapConversationResponses(
            List<Conversation> conversations,
            UUID userId,
            boolean archived,
            GroupConversationLabel groupLabelFilter) {
        Map<UUID, ConversationUserSetting> settingsByConversationId = getSettingsByConversationId(conversations,
                userId);

        return conversations.stream()
                .filter(conversation -> {
                    ConversationUserSetting setting = settingsByConversationId.get(conversation.getId());
                    boolean archivedState = setting != null && setting.getArchivedAt() != null;
                    return archivedState == archived;
                })
                .filter(conversation -> {
                    if (groupLabelFilter == null) {
                        return true;
                    }
                    if (conversation.getType() != ConversationType.GROUP) {
                        return false;
                    }
                    ConversationUserSetting setting = settingsByConversationId.get(conversation.getId());
                    return setting != null && setting.getGroupLabel() == groupLabelFilter;
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
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + conversationId,
                RealtimeEvent.of(
                        RealtimeEventType.CONVERSATION_UPDATED,
                        Map.of(
                                "id", conversation.getId(),
                                "conversationId", conversation.getId(),
                                "type", String.valueOf(conversation.getType()),
                                "name", conversation.getName() != null ? conversation.getName() : "",
                                "displayName", conversation.getName() != null ? conversation.getName() : "",
                                "avatarUrl", conversation.getAvatarUrl() != null ? conversation.getAvatarUrl() : "",
                                "backgroundType", resolveConversationBackgroundType(conversation),
                                "backgroundColor", conversation.getBackgroundColor() != null ? conversation.getBackgroundColor() : "",
                                "backgroundImageUrl", conversation.getBackgroundImageUrl() != null ? conversation.getBackgroundImageUrl() : "")));
    }

    private void createAndBroadcastGroupSystemMessage(
            Conversation conversation,
            UUID actorUserId,
            String kind,
            UUID targetUserId,
            Map<String, ?> metadata) {
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(actorUserId);
        message.setMessageType(MessageType.SYSTEM);
        message.setContent(buildGroupSystemMessageContent(conversation, actorUserId, kind, targetUserId, metadata));

        Message savedMessage = messageRepository.save(message);
        initializeSystemMessageStatuses(savedMessage.getId(), conversation.getId());
        initializeSystemMessageUserStates(savedMessage.getId(), conversation.getId(), actorUserId);

        MessageResponse response = MessageResponse.builder()
                .id(savedMessage.getId())
                .conversationId(savedMessage.getConversationId())
                .senderId(actorUserId)
                .senderDisplayName(resolveDisplayNameForUser(actorUserId))
                .senderAvatarUrl(resolveAvatarUrlForUser(actorUserId))
                .content(savedMessage.getContent())
                .type(MessageType.SYSTEM)
                .attachments(List.of())
                .reactions(List.of())
                .seen(true)
                .createdAt(savedMessage.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(),
                RealtimeEvent.of(RealtimeEventType.MESSAGE_CREATED, response));
    }

    private String buildGroupSystemMessageContent(
            Conversation conversation,
            UUID actorUserId,
            String kind,
            UUID targetUserId,
            Map<String, ?> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind);
        payload.put("actorId", actorUserId);
        payload.put("actorName", resolveDisplayNameForUser(actorUserId));
        payload.put("targetUserId", targetUserId);
        payload.put("targetName", targetUserId != null ? resolveDisplayNameForUser(targetUserId) : null);
        payload.put("conversationId", conversation.getId());
        payload.put("conversationName", conversation.getName());
        payload.put("createdAt", Instant.now().toString());
        if (metadata != null) {
            payload.putAll(metadata);
        }

        try {
            return GROUP_SYSTEM_PREFIX + objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("[GROUP SYSTEM MESSAGE] Failed to serialize payload kind={} conversationId={}",
                    kind,
                    conversation.getId(),
                    ex);
            return GROUP_SYSTEM_PREFIX + "{\"kind\":\"" + kind + "\"}";
        }
    }

    private void initializeSystemMessageStatuses(Long messageId, UUID conversationId) {
        Instant now = Instant.now();
        conversationMemberRepository.findByConversationId(conversationId).forEach(member -> {
            MessageStatus status = new MessageStatus();
            status.setMessageId(messageId);
            status.setUserId(member.getUserId());
            status.setStatus(MessageDeliveryStatus.SENT);
            status.setUpdatedAt(now);
            messageStatusRepository.save(status);
        });
    }

    private void initializeSystemMessageUserStates(Long messageId, UUID conversationId, UUID actorUserId) {
        Instant now = Instant.now();
        conversationMemberRepository.findByConversationId(conversationId).forEach(member -> {
            MessageUserState state = new MessageUserState();
            state.setMessageId(messageId);
            state.setUserId(member.getUserId());
            if (member.getUserId().equals(actorUserId)) {
                state.setSeenAt(now);
            }
            messageUserStateRepository.save(state);
        });
    }

    private String resolveConversationPreviewText(Message message) {
        if (message.getMessageType() == MessageType.SYSTEM) {
            return resolveGroupSystemPreviewText(message.getContent());
        }

        return message.getContent();
    }

    private String resolveGroupSystemPreviewText(String content) {
        Map<String, Object> payload = parseGroupSystemPayload(content);
        String kind = String.valueOf(payload.getOrDefault("kind", ""));
        String actorName = String.valueOf(payload.getOrDefault("actorName", "Ai đó"));
        String targetName = String.valueOf(payload.getOrDefault("targetName", "một thành viên"));
        String nickname = String.valueOf(payload.getOrDefault("nickname", ""));
        String conversationName = String.valueOf(payload.getOrDefault("conversationName", "nhóm"));

        return switch (kind) {
            case "group_member_added" -> actorName + " đã thêm " + targetName + " vào nhóm";
            case "group_member_removed" -> actorName + " đã xóa " + targetName + " khỏi nhóm";
            case "group_left" -> actorName + " đã rời nhóm";
            case "group_admin_promoted" -> actorName + " đã cấp phó nhóm cho " + targetName;
            case "group_admin_demoted" -> actorName + " đã thu hồi phó nhóm của " + targetName;
            case "group_owner_transferred" -> actorName + " đã chuyển quyền trưởng nhóm cho " + targetName;
            case "group_renamed" -> actorName + " đã đổi tên nhóm";
            case "group_avatar_changed" -> actorName + " đã cập nhật ảnh nhóm";
            case "group_background_changed" -> actorName + " đã đổi nền chat";
            case "group_nickname_changed" -> {
                if (nickname != null && !nickname.isBlank()) {
                    yield actorName + " đã đổi biệt danh của " + targetName + " thành \"" + nickname + "\"";
                }
                yield actorName + " đã xóa biệt danh của " + targetName;
            }
            case "group_disbanded" -> actorName + " đã giải tán nhóm " + conversationName;
            default -> "Hoạt động nhóm";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGroupSystemPayload(String content) {
        String normalized = content == null ? "" : content.trim();
        if (!normalized.startsWith(GROUP_SYSTEM_PREFIX)) {
            return Map.of();
        }

        try {
            Object value = objectMapper.readValue(normalized.substring(GROUP_SYSTEM_PREFIX.length()), Map.class);
            return value instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String resolveDisplayNameForUser(UUID userId) {
        return userProfileRepository.findById(userId)
                .map(profile -> resolveUserDisplayName(profile, userId))
                .orElse(userId != null ? userId.toString() : "Người dùng");
    }

    private String resolveAvatarUrlForUser(UUID userId) {
        return userProfileRepository.findById(userId)
                .map(UserProfile::getAvatarUrl)
                .map(this::normalizeNullableText)
                .orElse(null);
    }

    private void safeDispatchGroupDisbandedNotification(
            Conversation conversation,
            UUID actorUserId,
            List<UUID> recipientIds) {
        if (conversation == null || actorUserId == null || recipientIds == null || recipientIds.isEmpty()) {
            return;
        }

        try {
            NotificationDispatchResult result = notificationDispatcher.dispatch(
                    NotificationDispatchRequest.builder()
                            .type(NotificationType.GROUP_DISBANDED)
                            .targetType(NotificationTargetType.CONVERSATION)
                            .targetId(conversation.getId())
                            .actorId(actorUserId)
                            .conversationId(conversation.getId())
                            .explicitRecipientIds(recipientIds)
                            .recipientDirectlyAffected(true)
                            .metadata(Map.of(
                                    "actorName", resolveDisplayNameForUser(actorUserId),
                                    "conversationName",
                                    (conversation.getName() != null && !conversation.getName().isBlank())
                                            ? conversation.getName()
                                            : "Nhóm"))
                            .dedupKeyPrefix("group:" + conversation.getId() + ":disbanded")
                            .build());
            log.info("[GROUP DISBAND NOTIFICATION] conversationId={} actorUserId={} recipients={} pushSuccess={} denied={}",
                    conversation.getId(),
                    actorUserId,
                    recipientIds.size(),
                    result.getPushSuccessCount(),
                    result.getDeniedRecipients().size());
        } catch (Exception ex) {
            log.warn("[GROUP DISBAND NOTIFICATION] dispatch failed conversationId={} actorUserId={} reason={}",
                    conversation.getId(),
                    actorUserId,
                    ex.getMessage());
        }
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

    private ConversationGroupLabelResponse mapConversationGroupLabelResponse(
            UUID conversationId,
            UUID userId,
            ConversationUserSetting setting) {
        GroupConversationLabel label = setting != null ? setting.getGroupLabel() : null;
        return ConversationGroupLabelResponse.builder()
                .conversationId(conversationId)
                .userId(userId)
                .groupLabel(label != null ? label.name() : null)
                .groupLabelDisplayName(label != null ? label.getDisplayName() : null)
                .groupLabelColor(label != null ? label.getColor() : null)
                .updatedAt(setting != null ? setting.getUpdatedAt() : null)
                .build();
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

    private void ensureGroupConversationForLabel(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BusinessException("Only group conversations support group labels");
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

    private GroupConversationLabel normalizeGroupConversationLabel(String groupLabelCode) {
        if (groupLabelCode == null) {
            return null;
        }

        String normalizedCode = groupLabelCode.trim();
        if (normalizedCode.isEmpty()) {
            return null;
        }

        return GroupConversationLabel.fromCode(normalizedCode)
                .orElseThrow(() -> new BusinessException("Invalid group label"));
    }

    private ConversationNotificationLevel resolveNotificationLevel(ConversationUserSetting setting) {
        return setting != null && setting.getNotificationLevel() != null
                ? setting.getNotificationLevel()
                : ConversationNotificationLevel.ALL;
    }

    private boolean isMuted(ConversationUserSetting setting) {
        if (setting == null) {
            return false;
        }
        ConversationNotificationLevel notificationLevel = resolveNotificationLevel(setting);
        if (notificationLevel == ConversationNotificationLevel.NONE) {
            return true;
        }
        if (setting.getMutedUntil() != null) {
            return setting.getMutedUntil().isAfter(Instant.now());
        }
        return setting.getMutedAt() != null;
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

    private String normalizeMemberNickname(String nickname) {
        if (nickname == null) {
            return null;
        }

        String normalizedNickname = nickname.trim();
        if (normalizedNickname.isEmpty()) {
            return null;
        }
        if (normalizedNickname.length() > MAX_MEMBER_NICKNAME_LENGTH) {
            throw new BusinessException("Member nickname must be less than 100 characters");
        }

        return normalizedNickname;
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
                .nickname(normalizeNullableText(member.getNickname()))
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
