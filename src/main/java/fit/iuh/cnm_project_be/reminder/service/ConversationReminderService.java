package fit.iuh.cnm_project_be.reminder.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.enums.NotificationTargetType;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.message.service.MessageService;
import fit.iuh.cnm_project_be.reminder.dto.request.CreateConversationReminderRequest;
import fit.iuh.cnm_project_be.reminder.dto.request.UpdateConversationReminderRequest;
import fit.iuh.cnm_project_be.reminder.dto.response.ConversationReminderResponse;
import fit.iuh.cnm_project_be.reminder.dto.response.ReminderPageResponse;
import fit.iuh.cnm_project_be.reminder.dto.response.ReminderParticipantResponse;
import fit.iuh.cnm_project_be.reminder.entity.ConversationReminder;
import fit.iuh.cnm_project_be.reminder.entity.ConversationReminderParticipant;
import fit.iuh.cnm_project_be.reminder.enums.ReminderParticipantStatus;
import fit.iuh.cnm_project_be.reminder.enums.ReminderScope;
import fit.iuh.cnm_project_be.reminder.enums.ReminderStatus;
import fit.iuh.cnm_project_be.reminder.repository.ConversationReminderParticipantRepository;
import fit.iuh.cnm_project_be.reminder.repository.ConversationReminderRepository;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationReminderService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int DUE_BATCH_SIZE = 200;
    private static final long MAX_PAST_ALLOWANCE_SECONDS = 60L;

    private final ConversationReminderRepository reminderRepository;
    private final ConversationReminderParticipantRepository participantRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserProfileRepository userProfileRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final MessageService messageService;

    @Transactional
    public ConversationReminderResponse createReminder(
            UUID conversationId,
            UUID actorUserId,
            CreateConversationReminderRequest request) {
        Conversation conversation = getConversationOrThrow(conversationId);
        ensureConversationMember(conversationId, actorUserId);

        Instant remindAt = normalizeRemindAt(request.getRemindAt());
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
        Set<UUID> participantIds = resolveParticipantIds(members, request.getParticipantIds());

        ConversationReminder reminder = new ConversationReminder();
        reminder.setConversationId(conversationId);
        reminder.setCreatedBy(actorUserId);
        reminder.setTitle(normalizeTitle(request.getTitle()));
        reminder.setDescription(normalizeNullableText(request.getDescription()));
        reminder.setRemindAt(remindAt);
        reminder.setTimezone(normalizeNullableText(request.getTimezone()));
        reminder.setStatus(ReminderStatus.SCHEDULED);
        reminder.setRecurrenceRule(null);
        reminder.setDueNotifiedAt(null);
        reminder.setCompletedAt(null);
        reminder.setCancelledAt(null);
        reminder.setDeletedAt(null);

        ConversationReminder savedReminder = reminderRepository.save(reminder);
        replaceParticipants(savedReminder.getId(), participantIds);
        publishReminderSystemMessage(savedReminder, actorUserId, "tạo nhắc hẹn");
        return mapReminderResponse(savedReminder, conversation, participantRepository.findByReminderId(savedReminder.getId()));
    }

    @Transactional(readOnly = true)
    public ReminderPageResponse getConversationReminders(
            UUID conversationId,
            UUID actorUserId,
            String statusCode,
            Instant fromTime,
            Instant toTime,
            Integer page,
            Integer size) {
        ensureConversationMember(conversationId, actorUserId);
        ReminderStatus statusFilter = normalizeStatus(statusCode);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);

        Page<ConversationReminder> reminderPage = reminderRepository.findConversationReminders(
                conversationId,
                statusFilter,
                fromTime,
                toTime,
                PageRequest.of(normalizedPage, normalizedSize));
        return toPageResponse(reminderPage);
    }

    @Transactional(readOnly = true)
    public ReminderPageResponse getMyReminders(
            UUID actorUserId,
            String statusCode,
            String scopeCode,
            Instant fromTime,
            Instant toTime,
            Integer page,
            Integer size) {
        ReminderStatus statusFilter = normalizeStatus(statusCode);
        ReminderScope scope = normalizeScope(scopeCode);
        TimeWindow window = resolveTimeWindow(scope, fromTime, toTime);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);

        Page<ConversationReminder> reminderPage = reminderRepository.findParticipantReminders(
                actorUserId,
                statusFilter,
                window.from(),
                window.to(),
                PageRequest.of(normalizedPage, normalizedSize));
        return toPageResponse(reminderPage);
    }

    @Transactional
    public ConversationReminderResponse updateReminder(
            UUID reminderId,
            UUID actorUserId,
            UpdateConversationReminderRequest request) {
        ConversationReminder reminder = getReminderOrThrow(reminderId);
        ensureCreator(reminder, actorUserId);
        ensureEditable(reminder);

        if (request.getTitle() != null) {
            reminder.setTitle(normalizeTitle(request.getTitle()));
        }
        if (request.getDescription() != null) {
            reminder.setDescription(normalizeNullableText(request.getDescription()));
        }
        if (request.getTimezone() != null) {
            reminder.setTimezone(normalizeNullableText(request.getTimezone()));
        }
        if (request.getRemindAt() != null) {
            Instant nextRemindAt = normalizeRemindAt(request.getRemindAt());
            reminder.setRemindAt(nextRemindAt);
            if (nextRemindAt.isAfter(Instant.now()) && reminder.getStatus() == ReminderStatus.DUE) {
                reminder.setStatus(ReminderStatus.SCHEDULED);
                reminder.setDueNotifiedAt(null);
            }
        }

        ConversationReminder savedReminder = reminderRepository.save(reminder);
        if (request.getParticipantIds() != null) {
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(reminder.getConversationId());
            Set<UUID> participantIds = resolveParticipantIds(members, request.getParticipantIds());
            replaceParticipants(savedReminder.getId(), participantIds);
        }

        Conversation conversation = getConversationOrThrow(savedReminder.getConversationId());
        publishReminderSystemMessage(savedReminder, actorUserId, "cập nhật nhắc hẹn");
        return mapReminderResponse(
                savedReminder,
                conversation,
                participantRepository.findByReminderId(savedReminder.getId()));
    }

    @Transactional
    public ConversationReminderResponse cancelReminder(UUID reminderId, UUID actorUserId) {
        return cancelReminderInternal(reminderId, actorUserId, "hủy nhắc hẹn");
    }

    private ConversationReminderResponse cancelReminderInternal(UUID reminderId, UUID actorUserId, String actionText) {
        ConversationReminder reminder = getReminderOrThrow(reminderId);
        ensureCreator(reminder, actorUserId);
        if (reminder.getStatus() == ReminderStatus.CANCELLED) {
            return mapReminderResponse(reminder);
        }

        reminder.setStatus(ReminderStatus.CANCELLED);
        reminder.setCancelledAt(Instant.now());
        ConversationReminder savedReminder = reminderRepository.save(reminder);
        publishReminderSystemMessage(savedReminder, actorUserId, actionText);
        return mapReminderResponse(savedReminder);
    }

    @Transactional
    public ConversationReminderResponse deleteReminder(UUID reminderId, UUID actorUserId) {
        return cancelReminderInternal(reminderId, actorUserId, "xóa nhắc hẹn");
    }

    @Transactional
    public ConversationReminderResponse completeReminder(UUID reminderId, UUID actorUserId) {
        ConversationReminder reminder = getReminderOrThrow(reminderId);
        List<ConversationReminderParticipant> participants = participantRepository.findByReminderId(reminderId);
        Optional<ConversationReminderParticipant> actorParticipant = participants.stream()
                .filter(participant -> Objects.equals(participant.getUserId(), actorUserId))
                .findFirst();

        if (Objects.equals(reminder.getCreatedBy(), actorUserId)) {
            reminder.setStatus(ReminderStatus.COMPLETED);
            reminder.setCompletedAt(Instant.now());
            participants.forEach(participant -> participant.setStatus(ReminderParticipantStatus.DONE));
            participantRepository.saveAll(participants);
            return mapReminderResponse(reminderRepository.save(reminder));
        }

        ConversationReminderParticipant participant = actorParticipant
                .orElseThrow(() -> new ForbiddenException("Only reminder participants can mark done"));
        participant.setStatus(ReminderParticipantStatus.DONE);
        participant.setAcknowledgedAt(Instant.now());
        participantRepository.save(participant);

        boolean allDone = participants.stream().allMatch(item ->
                item.getUserId().equals(actorUserId)
                        ? ReminderParticipantStatus.DONE == participant.getStatus()
                        : item.getStatus() == ReminderParticipantStatus.DONE
                        || item.getStatus() == ReminderParticipantStatus.DISMISSED);
        if (allDone && reminder.getStatus() != ReminderStatus.COMPLETED) {
            reminder.setStatus(ReminderStatus.COMPLETED);
            reminder.setCompletedAt(Instant.now());
            reminderRepository.save(reminder);
        }

        return mapReminderResponse(reminder);
    }

    @Transactional
    public ConversationReminderResponse acknowledgeReminder(UUID reminderId, UUID actorUserId) {
        ConversationReminder reminder = getReminderOrThrow(reminderId);
        ConversationReminderParticipant participant = participantRepository
                .findByReminderIdAndUserId(reminderId, actorUserId)
                .orElseThrow(() -> new ForbiddenException("Only reminder participants can acknowledge reminder"));
        participant.setStatus(ReminderParticipantStatus.ACKNOWLEDGED);
        participant.setAcknowledgedAt(Instant.now());
        participantRepository.save(participant);
        return mapReminderResponse(reminder);
    }

    @Transactional
    public ConversationReminderResponse dismissReminder(UUID reminderId, UUID actorUserId) {
        ConversationReminder reminder = getReminderOrThrow(reminderId);
        ConversationReminderParticipant participant = participantRepository
                .findByReminderIdAndUserId(reminderId, actorUserId)
                .orElseThrow(() -> new ForbiddenException("Only reminder participants can dismiss reminder"));
        participant.setStatus(ReminderParticipantStatus.DISMISSED);
        participant.setDismissedAt(Instant.now());
        participantRepository.save(participant);
        return mapReminderResponse(reminder);
    }

    @Transactional
    public int processDueReminders(Instant now) {
        Instant executionTime = now != null ? now : Instant.now();
        List<ConversationReminder> dueReminders = reminderRepository.findDueReminders(
                ReminderStatus.SCHEDULED,
                executionTime,
                PageRequest.of(0, DUE_BATCH_SIZE));
        if (dueReminders.isEmpty()) {
            return 0;
        }

        int processedCount = 0;
        for (ConversationReminder reminder : dueReminders) {
            try {
                boolean dispatched = dispatchDueReminder(reminder);
                if (dispatched) {
                    reminder.setStatus(ReminderStatus.DUE);
                    reminder.setDueNotifiedAt(executionTime);
                    reminderRepository.save(reminder);
                    processedCount++;
                }
            } catch (Exception ex) {
                log.warn("[ReminderScheduler] failed to process reminderId={} reason={}",
                        reminder.getId(),
                        ex.getMessage());
            }
        }

        return processedCount;
    }

    private boolean dispatchDueReminder(ConversationReminder reminder) {
        List<ConversationReminderParticipant> participants = participantRepository.findByReminderId(reminder.getId());
        List<UUID> recipients = participants.stream()
                .filter(participant ->
                        participant.getStatus() != ReminderParticipantStatus.DISMISSED
                                && participant.getStatus() != ReminderParticipantStatus.DONE)
                .map(ConversationReminderParticipant::getUserId)
                .distinct()
                .toList();

        if (recipients.isEmpty()) {
            return true;
        }

        Conversation conversation = conversationRepository.findById(reminder.getConversationId()).orElse(null);
        String conversationName = normalizeNullableText(conversation != null ? conversation.getName() : null);
        String actorName = userProfileRepository.findById(reminder.getCreatedBy())
                .map(this::resolveUserDisplayName)
                .orElse("Ai đó");

        notificationDispatcher.dispatch(NotificationDispatchRequest.builder()
                .type(NotificationType.REMINDER_DUE)
                .targetType(NotificationTargetType.REMINDER)
                .targetId(reminder.getId())
                .actorId(reminder.getCreatedBy())
                .explicitRecipientIds(recipients)
                .conversationId(reminder.getConversationId())
                .title("Nhắc hẹn đến giờ")
                .body(reminder.getTitle())
                .genericBody("Bạn có một nhắc hẹn đến hạn")
                .metadata(Map.of(
                        "actorName", actorName,
                        "conversationName", defaultIfBlank(conversationName, "Cuộc trò chuyện"),
                        "reminderId", reminder.getId().toString(),
                        "reminderTitle", reminder.getTitle(),
                        "remindAt", reminder.getRemindAt().toString()
                ))
                .dedupKeyPrefix(NotificationType.REMINDER_DUE.name() + ":" + reminder.getId())
                .recipientDirectlyAffected(true)
                .build());

        return true;
    }

    private ReminderPageResponse toPageResponse(Page<ConversationReminder> reminderPage) {
        List<ConversationReminder> reminders = reminderPage.getContent();
        if (reminders.isEmpty()) {
            return ReminderPageResponse.builder()
                    .items(List.of())
                    .page(reminderPage.getNumber())
                    .size(reminderPage.getSize())
                    .totalItems(reminderPage.getTotalElements())
                    .totalPages(reminderPage.getTotalPages())
                    .hasMore(reminderPage.hasNext())
                    .build();
        }

        Map<UUID, Conversation> conversationById = conversationRepository.findAllById(reminders.stream()
                        .map(ConversationReminder::getConversationId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Conversation::getId, value -> value));

        List<UUID> reminderIds = reminders.stream().map(ConversationReminder::getId).toList();
        Map<UUID, List<ConversationReminderParticipant>> participantsByReminderId = participantRepository
                .findByReminderIdIn(reminderIds).stream()
                .collect(Collectors.groupingBy(ConversationReminderParticipant::getReminderId));

        Set<UUID> profileUserIds = new LinkedHashSet<>();
        reminders.forEach(reminder -> profileUserIds.add(reminder.getCreatedBy()));
        participantsByReminderId.values().forEach(participants -> participants.forEach(
                participant -> profileUserIds.add(participant.getUserId())));

        Map<UUID, UserProfile> profilesByUserId = userProfileRepository.findAllById(profileUserIds).stream()
                .filter(userProfile -> !userProfile.isDeleted())
                .collect(Collectors.toMap(UserProfile::getUserId, value -> value));

        List<ConversationReminderResponse> items = reminders.stream()
                .map(reminder -> mapReminderResponse(
                        reminder,
                        conversationById.get(reminder.getConversationId()),
                        participantsByReminderId.getOrDefault(reminder.getId(), List.of()),
                        profilesByUserId))
                .toList();

        return ReminderPageResponse.builder()
                .items(items)
                .page(reminderPage.getNumber())
                .size(reminderPage.getSize())
                .totalItems(reminderPage.getTotalElements())
                .totalPages(reminderPage.getTotalPages())
                .hasMore(reminderPage.hasNext())
                .build();
    }

    private ConversationReminderResponse mapReminderResponse(ConversationReminder reminder) {
        Conversation conversation = conversationRepository.findById(reminder.getConversationId()).orElse(null);
        List<ConversationReminderParticipant> participants = participantRepository.findByReminderId(reminder.getId());
        return mapReminderResponse(reminder, conversation, participants);
    }

    private ConversationReminderResponse mapReminderResponse(
            ConversationReminder reminder,
            Conversation conversation,
            List<ConversationReminderParticipant> participants) {
        Set<UUID> userIds = new LinkedHashSet<>();
        userIds.add(reminder.getCreatedBy());
        participants.forEach(participant -> userIds.add(participant.getUserId()));
        Map<UUID, UserProfile> profilesByUserId = userProfileRepository.findAllById(userIds).stream()
                .filter(userProfile -> !userProfile.isDeleted())
                .collect(Collectors.toMap(UserProfile::getUserId, value -> value));
        return mapReminderResponse(reminder, conversation, participants, profilesByUserId);
    }

    private ConversationReminderResponse mapReminderResponse(
            ConversationReminder reminder,
            Conversation conversation,
            List<ConversationReminderParticipant> participants,
            Map<UUID, UserProfile> profilesByUserId) {
        UserProfile creatorProfile = profilesByUserId.get(reminder.getCreatedBy());
        List<ReminderParticipantResponse> participantResponses = participants.stream()
                .map(participant -> {
                    UserProfile profile = profilesByUserId.get(participant.getUserId());
                    return ReminderParticipantResponse.builder()
                            .userId(participant.getUserId())
                            .displayName(resolveUserDisplayName(profile, participant.getUserId()))
                            .avatarUrl(profile != null ? normalizeNullableText(profile.getAvatarUrl()) : null)
                            .status(participant.getStatus() != null ? participant.getStatus().name() : null)
                            .readAt(participant.getReadAt())
                            .acknowledgedAt(participant.getAcknowledgedAt())
                            .dismissedAt(participant.getDismissedAt())
                            .build();
                })
                .toList();

        return ConversationReminderResponse.builder()
                .id(reminder.getId())
                .conversationId(reminder.getConversationId())
                .conversationName(conversation != null ? normalizeNullableText(conversation.getName()) : null)
                .createdBy(reminder.getCreatedBy())
                .createdByName(resolveUserDisplayName(creatorProfile, reminder.getCreatedBy()))
                .title(reminder.getTitle())
                .description(reminder.getDescription())
                .remindAt(reminder.getRemindAt())
                .timezone(reminder.getTimezone())
                .status(reminder.getStatus() != null ? reminder.getStatus().name() : null)
                .participants(participantResponses)
                .dueNotifiedAt(reminder.getDueNotifiedAt())
                .completedAt(reminder.getCompletedAt())
                .cancelledAt(reminder.getCancelledAt())
                .createdAt(reminder.getCreatedAt())
                .updatedAt(reminder.getUpdatedAt())
                .build();
    }

    private void replaceParticipants(UUID reminderId, Collection<UUID> participantIds) {
        participantRepository.deleteByReminderId(reminderId);
        List<ConversationReminderParticipant> participants = participantIds.stream()
                .distinct()
                .map(userId -> {
                    ConversationReminderParticipant participant = new ConversationReminderParticipant();
                    participant.setReminderId(reminderId);
                    participant.setUserId(userId);
                    participant.setStatus(ReminderParticipantStatus.PENDING);
                    return participant;
                })
                .toList();
        participantRepository.saveAll(participants);
    }

    private void publishReminderSystemMessage(ConversationReminder reminder, UUID actorUserId, String actionText) {
        String actorName = userProfileRepository.findById(actorUserId)
                .filter(profile -> !profile.isDeleted())
                .map(this::resolveUserDisplayName)
                .orElse("Ai đó");
        String reminderTitle = defaultIfBlank(reminder.getTitle(), "nhắc hẹn");
        messageService.createSystemMessage(
                reminder.getConversationId(),
                actorUserId,
                actorName + " " + actionText + " " + reminderTitle);
    }

    private Set<UUID> resolveParticipantIds(
            List<ConversationMember> members,
            Collection<UUID> requestedParticipantIds) {
        Set<UUID> memberIds = members.stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        if (memberIds.isEmpty()) {
            throw new BusinessException("Conversation has no active members");
        }

        if (requestedParticipantIds == null || requestedParticipantIds.isEmpty()) {
            return memberIds;
        }

        Set<UUID> normalizedParticipantIds = requestedParticipantIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedParticipantIds.isEmpty()) {
            throw new BusinessException("Reminder participants are required");
        }
        if (!memberIds.containsAll(normalizedParticipantIds)) {
            throw new BusinessException("Reminder participants must belong to the conversation");
        }
        return normalizedParticipantIds;
    }

    private ConversationReminder getReminderOrThrow(UUID reminderId) {
        return reminderRepository.findByIdAndDeletedAtIsNull(reminderId)
                .orElseThrow(() -> new NotFoundException("Reminder not found"));
    }

    private Conversation getConversationOrThrow(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        if (conversation.isDeleted()) {
            throw new NotFoundException("Conversation not found");
        }
        return conversation;
    }

    private void ensureConversationMember(UUID conversationId, UUID userId) {
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("User does not belong to this conversation"));
    }

    private void ensureCreator(ConversationReminder reminder, UUID userId) {
        if (!Objects.equals(reminder.getCreatedBy(), userId)) {
            throw new ForbiddenException("Only reminder creator can update or cancel reminder");
        }
    }

    private void ensureEditable(ConversationReminder reminder) {
        if (reminder.getStatus() == ReminderStatus.CANCELLED || reminder.getStatus() == ReminderStatus.COMPLETED) {
            throw new BusinessException("Reminder is no longer editable");
        }
    }

    private ReminderStatus normalizeStatus(String statusCode) {
        return ReminderStatus.fromCode(statusCode)
                .orElseGet(() -> {
                    if (statusCode == null || statusCode.isBlank()) {
                        return null;
                    }
                    throw new BusinessException("Invalid reminder status");
                });
    }

    private ReminderScope normalizeScope(String scopeCode) {
        return ReminderScope.fromCode(scopeCode)
                .orElseGet(() -> {
                    if (scopeCode == null || scopeCode.isBlank()) {
                        return null;
                    }
                    throw new BusinessException("Invalid reminder scope");
                });
    }

    private TimeWindow resolveTimeWindow(ReminderScope scope, Instant fromTime, Instant toTime) {
        if (fromTime != null || toTime != null || scope == null) {
            return new TimeWindow(fromTime, toTime);
        }

        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return switch (scope) {
            case TODAY -> new TimeWindow(
                    now.toLocalDate().atStartOfDay(zoneId).toInstant(),
                    now.toLocalDate().plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant());
            case WEEK -> new TimeWindow(
                    now.toLocalDate().atStartOfDay(zoneId).toInstant(),
                    now.plusDays(7).toInstant());
            case UPCOMING -> new TimeWindow(now.toInstant(), null);
            case PAST -> new TimeWindow(null, now.toInstant());
        };
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private Instant normalizeRemindAt(Instant remindAt) {
        if (remindAt == null) {
            throw new BusinessException("Reminder time is required");
        }
        Instant earliestAllowed = Instant.now().minusSeconds(MAX_PAST_ALLOWANCE_SECONDS);
        if (remindAt.isBefore(earliestAllowed)) {
            throw new BusinessException("Reminder time must be in the future");
        }
        return remindAt;
    }

    private String normalizeTitle(String title) {
        String normalizedTitle = normalizeNullableText(title);
        if (normalizedTitle == null) {
            throw new BusinessException("Reminder title is required");
        }
        if (normalizedTitle.length() > 255) {
            throw new BusinessException("Reminder title must be at most 255 characters");
        }
        return normalizedTitle;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private String resolveUserDisplayName(UserProfile userProfile) {
        return resolveUserDisplayName(userProfile, null);
    }

    private String resolveUserDisplayName(UserProfile userProfile, UUID fallbackUserId) {
        if (userProfile == null) {
            return fallbackUserId != null ? fallbackUserId.toString() : null;
        }

        String displayName = normalizeNullableText(userProfile.getDisplayName());
        if (displayName != null) {
            return displayName;
        }

        String firstName = normalizeNullableText(userProfile.getFirstName());
        String lastName = normalizeNullableText(userProfile.getLastName());
        String fullName = String.join(" ",
                firstName != null ? firstName : "",
                lastName != null ? lastName : "").trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }

        String username = normalizeNullableText(userProfile.getUsername());
        if (username != null) {
            return username;
        }

        UUID resolvedFallbackId = userProfile.getUserId() != null ? userProfile.getUserId() : fallbackUserId;
        return resolvedFallbackId != null ? resolvedFallbackId.toString() : null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record TimeWindow(Instant from, Instant to) {
    }
}
