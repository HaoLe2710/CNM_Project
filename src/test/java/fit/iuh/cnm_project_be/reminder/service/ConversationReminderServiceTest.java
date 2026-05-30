package fit.iuh.cnm_project_be.reminder.service;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchRequest;
import fit.iuh.cnm_project_be.notification.dto.NotificationDispatchResult;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import fit.iuh.cnm_project_be.notification.service.NotificationDispatcher;
import fit.iuh.cnm_project_be.reminder.dto.request.CreateConversationReminderRequest;
import fit.iuh.cnm_project_be.reminder.dto.response.ConversationReminderResponse;
import fit.iuh.cnm_project_be.reminder.entity.ConversationReminder;
import fit.iuh.cnm_project_be.reminder.entity.ConversationReminderParticipant;
import fit.iuh.cnm_project_be.reminder.enums.ReminderParticipantStatus;
import fit.iuh.cnm_project_be.reminder.enums.ReminderStatus;
import fit.iuh.cnm_project_be.reminder.repository.ConversationReminderParticipantRepository;
import fit.iuh.cnm_project_be.reminder.repository.ConversationReminderRepository;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.enums.MemberRole;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.user.entity.UserProfile;
import fit.iuh.cnm_project_be.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationReminderServiceTest {

    @Mock
    private ConversationReminderRepository reminderRepository;
    @Mock
    private ConversationReminderParticipantRepository participantRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private ConversationReminderService reminderService;

    private UUID conversationId;
    private UUID creatorId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        conversationId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        when(notificationDispatcher.dispatch(any())).thenReturn(NotificationDispatchResult.builder().build());
    }

    @Test
    void createReminderPrivateConversationSuccess() {
        Conversation conversation = conversation(conversationId, creatorId, ConversationType.PRIVATE);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, creatorId))
                .thenReturn(Optional.of(member(conversationId, creatorId)));
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, creatorId), member(conversationId, memberId)));
        when(reminderRepository.save(any(ConversationReminder.class))).thenAnswer(invocation -> {
            ConversationReminder reminder = invocation.getArgument(0);
            reminder.setId(UUID.randomUUID());
            reminder.setCreatedAt(Instant.now());
            reminder.setUpdatedAt(Instant.now());
            return reminder;
        });
        when(participantRepository.findByReminderId(any(UUID.class)))
                .thenReturn(List.of(
                        participant(UUID.randomUUID(), creatorId, ReminderParticipantStatus.PENDING),
                        participant(UUID.randomUUID(), memberId, ReminderParticipantStatus.PENDING)));
        when(userProfileRepository.findAllById(any())).thenReturn(List.of(
                profile(creatorId, "Creator"),
                profile(memberId, "Member")));

        CreateConversationReminderRequest request = new CreateConversationReminderRequest();
        request.setTitle("Họp nhóm CNM");
        request.setDescription("Chuẩn bị demo");
        request.setRemindAt(Instant.now().plusSeconds(1800));
        request.setTimezone("Asia/Ho_Chi_Minh");

        ConversationReminderResponse response = reminderService.createReminder(conversationId, creatorId, request);

        assertThat(response.getConversationId()).isEqualTo(conversationId);
        assertThat(response.getTitle()).isEqualTo("Họp nhóm CNM");
        assertThat(response.getStatus()).isEqualTo(ReminderStatus.SCHEDULED.name());
        assertThat(response.getParticipants()).hasSize(2);
        verify(participantRepository).saveAll(any());
    }

    @Test
    void createReminderNonMemberForbidden() {
        Conversation conversation = conversation(conversationId, creatorId, ConversationType.PRIVATE);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, creatorId))
                .thenReturn(Optional.empty());

        CreateConversationReminderRequest request = new CreateConversationReminderRequest();
        request.setTitle("Reminder");
        request.setRemindAt(Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> reminderService.createReminder(conversationId, creatorId, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createReminderParticipantNotMemberRejected() {
        Conversation conversation = conversation(conversationId, creatorId, ConversationType.GROUP);
        UUID outsiderId = UUID.randomUUID();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationMemberRepository.findByConversationIdAndUserId(conversationId, creatorId))
                .thenReturn(Optional.of(member(conversationId, creatorId)));
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(member(conversationId, creatorId), member(conversationId, memberId)));

        CreateConversationReminderRequest request = new CreateConversationReminderRequest();
        request.setTitle("Reminder");
        request.setRemindAt(Instant.now().plusSeconds(300));
        request.setParticipantIds(List.of(outsiderId));

        assertThatThrownBy(() -> reminderService.createReminder(conversationId, creatorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("participants");
    }

    @Test
    void processDueRemindersSendsNotificationAndMarksDue() {
        UUID reminderId = UUID.randomUUID();
        Instant now = Instant.now();
        ConversationReminder reminder = scheduledReminder(conversationId, creatorId, now.minusSeconds(10));
        reminder.setId(reminderId);
        reminder.setCreatedAt(now.minusSeconds(100));
        reminder.setUpdatedAt(now.minusSeconds(100));

        when(reminderRepository.findDueReminders(eq(ReminderStatus.SCHEDULED), any(), any(PageRequest.class)))
                .thenReturn(List.of(reminder));
        when(participantRepository.findByReminderId(reminderId))
                .thenReturn(List.of(participant(reminderId, memberId, ReminderParticipantStatus.PENDING)));
        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, creatorId, ConversationType.GROUP)));
        when(userProfileRepository.findById(creatorId))
                .thenReturn(Optional.of(profile(creatorId, "Creator")));
        when(reminderRepository.save(any(ConversationReminder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = reminderService.processDueReminders(now);

        assertThat(processed).isEqualTo(1);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DUE);
        assertThat(reminder.getDueNotifiedAt()).isEqualTo(now);

        ArgumentCaptor<NotificationDispatchRequest> captor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcher, atLeastOnce()).dispatch(captor.capture());
        NotificationDispatchRequest dispatchRequest = captor.getValue();
        assertThat(dispatchRequest.getType()).isEqualTo(NotificationType.REMINDER_DUE);
        assertThat(dispatchRequest.getExplicitRecipientIds()).containsExactly(memberId);
        assertThat(dispatchRequest.getDedupKeyPrefix()).isEqualTo(NotificationType.REMINDER_DUE.name() + ":" + reminderId);
    }

    @Test
    void processDueRemindersSkipsWhenNoDueItems() {
        when(reminderRepository.findDueReminders(eq(ReminderStatus.SCHEDULED), any(), any(PageRequest.class)))
                .thenReturn(List.of());

        int processed = reminderService.processDueReminders(Instant.now());

        assertThat(processed).isEqualTo(0);
        verify(notificationDispatcher, never()).dispatch(any());
    }

    private Conversation conversation(UUID id, UUID creator, ConversationType type) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setCreatorId(creator);
        conversation.setType(type);
        conversation.setName(type == ConversationType.GROUP ? "Nhóm CNM" : "Chat riêng");
        return conversation;
    }

    private ConversationMember member(UUID conversationId, UUID userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER);
        return member;
    }

    private ConversationReminder scheduledReminder(UUID conversationId, UUID createdBy, Instant remindAt) {
        ConversationReminder reminder = new ConversationReminder();
        reminder.setConversationId(conversationId);
        reminder.setCreatedBy(createdBy);
        reminder.setTitle("Reminder");
        reminder.setDescription("desc");
        reminder.setRemindAt(remindAt);
        reminder.setTimezone("Asia/Ho_Chi_Minh");
        reminder.setStatus(ReminderStatus.SCHEDULED);
        return reminder;
    }

    private ConversationReminderParticipant participant(
            UUID reminderId,
            UUID userId,
            ReminderParticipantStatus status) {
        ConversationReminderParticipant participant = new ConversationReminderParticipant();
        participant.setReminderId(reminderId);
        participant.setUserId(userId);
        participant.setStatus(status);
        return participant;
    }

    private UserProfile profile(UUID userId, String displayName) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setDisplayName(displayName);
        profile.setUsername(displayName.toLowerCase());
        return profile;
    }
}
