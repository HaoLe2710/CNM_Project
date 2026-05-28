package fit.iuh.cnm_project_be.notification.service;

import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.common.exception.NotFoundException;
import fit.iuh.cnm_project_be.notification.dto.ConversationNotificationSettingRequest;
import fit.iuh.cnm_project_be.notification.dto.ConversationNotificationSettingResponse;
import fit.iuh.cnm_project_be.room.entity.Conversation;
import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import fit.iuh.cnm_project_be.room.enums.ConversationType;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationRepository;
import fit.iuh.cnm_project_be.room.repository.ConversationUserSettingRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationNotificationSettingServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-28T10:30:00Z");

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private ConversationUserSettingRepository conversationUserSettingRepository;
    @Mock
    private UserService userService;

    private ConversationNotificationSettingService service;

    @BeforeEach
    void setUp() {
        service = new ConversationNotificationSettingService(
                conversationRepository,
                conversationMemberRepository,
                conversationUserSettingRepository,
                userService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(conversationUserSettingRepository.save(any(ConversationUserSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getSettingMissingSettingReturnsDefaultAll() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockCurrentMember(conversationId, userId);
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        ConversationNotificationSettingResponse response = service.getMySetting(conversationId);

        assertThat(response.getNotificationLevel()).isEqualTo(ConversationNotificationLevel.ALL);
        assertThat(response.isMuted()).isFalse();
        assertThat(response.getMutedUntil()).isNull();
    }

    @Test
    void updateSettingToMentionsOnlySuccess() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockCurrentMember(conversationId, userId);
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        ConversationNotificationSettingRequest request = new ConversationNotificationSettingRequest();
        request.setNotificationLevel(ConversationNotificationLevel.MENTIONS_ONLY);

        ConversationNotificationSettingResponse response = service.updateMySetting(conversationId, request);

        assertThat(response.getNotificationLevel()).isEqualTo(ConversationNotificationLevel.MENTIONS_ONLY);
        assertThat(response.isMuted()).isFalse();
        verify(conversationUserSettingRepository).save(any(ConversationUserSetting.class));
    }

    @Test
    void updateSettingToNoneSuccess() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockCurrentMember(conversationId, userId);
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        ConversationNotificationSettingRequest request = new ConversationNotificationSettingRequest();
        request.setNotificationLevel(ConversationNotificationLevel.NONE);

        ConversationNotificationSettingResponse response = service.updateMySetting(conversationId, request);

        assertThat(response.getNotificationLevel()).isEqualTo(ConversationNotificationLevel.NONE);
        assertThat(response.isMuted()).isTrue();
        assertThat(response.getLastMutedAt()).isEqualTo(NOW);
    }

    @Test
    void updateSettingMuteUntilFutureSetsMutedUntilAndLastMutedAt() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant future = NOW.plusSeconds(3600);
        mockCurrentMember(conversationId, userId);
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        ConversationNotificationSettingRequest request = new ConversationNotificationSettingRequest();
        request.setNotificationLevel(ConversationNotificationLevel.ALL);
        request.setMutedUntil(future);

        ConversationNotificationSettingResponse response = service.updateMySetting(conversationId, request);

        assertThat(response.getMutedUntil()).isEqualTo(future);
        assertThat(response.getLastMutedAt()).isEqualTo(NOW);
        assertThat(response.isMuted()).isTrue();
    }

    @Test
    void updateSettingMuteUntilPastNormalizesToNull() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mockCurrentMember(conversationId, userId);
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty());

        ConversationNotificationSettingRequest request = new ConversationNotificationSettingRequest();
        request.setNotificationLevel(ConversationNotificationLevel.ALL);
        request.setMutedUntil(NOW.minusSeconds(60));

        ConversationNotificationSettingResponse response = service.updateMySetting(conversationId, request);

        assertThat(response.getMutedUntil()).isNull();
        assertThat(response.isMuted()).isFalse();
    }

    @Test
    void updateSettingUnmuteSetsAllAndMutedUntilNull() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ConversationUserSetting setting = setting(conversationId, userId);
        setting.setNotificationLevel(ConversationNotificationLevel.NONE);
        setting.setMutedAt(NOW.minusSeconds(60));
        setting.setMutedUntil(NOW.plusSeconds(3600));
        mockCurrentMember(conversationId, userId);
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(setting));

        ConversationNotificationSettingRequest request = new ConversationNotificationSettingRequest();
        request.setNotificationLevel(ConversationNotificationLevel.ALL);
        request.setMutedUntil(null);

        ConversationNotificationSettingResponse response = service.updateMySetting(conversationId, request);

        assertThat(response.getNotificationLevel()).isEqualTo(ConversationNotificationLevel.ALL);
        assertThat(response.getMutedUntil()).isNull();
        assertThat(response.isMuted()).isFalse();
    }

    @Test
    void updateSettingNonMemberForbidden() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation(conversationId)));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.updateMySetting(conversationId, new ConversationNotificationSettingRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateSettingInvalidConversationNotFound() {
        UUID conversationId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMySetting(conversationId, new ConversationNotificationSettingRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getSettingExistingMutedReturnsMutedTrue() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ConversationUserSetting setting = setting(conversationId, userId);
        setting.setMutedUntil(NOW.plusSeconds(60));
        mockCurrentMember(conversationId, userId);
        when(conversationUserSettingRepository.findByConversationIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(setting));

        ConversationNotificationSettingResponse response = service.getMySetting(conversationId);

        assertThat(response.isMuted()).isTrue();
    }

    private void mockCurrentMember(UUID conversationId, UUID userId) {
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation(conversationId)));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
    }

    private Conversation conversation(UUID conversationId) {
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setCreatorId(UUID.randomUUID());
        conversation.setType(ConversationType.GROUP);
        return conversation;
    }

    private ConversationUserSetting setting(UUID conversationId, UUID userId) {
        ConversationUserSetting setting = new ConversationUserSetting();
        setting.setConversationId(conversationId);
        setting.setUserId(userId);
        return setting;
    }
}
