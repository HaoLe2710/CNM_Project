package fit.iuh.cnm_project_be.presence.service;

import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.presence.dto.response.ConversationPresenceResponse;
import fit.iuh.cnm_project_be.presence.dto.response.PresenceItemResponse;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PresenceRealtimePublisher presenceRealtimePublisher;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private UserService userService;

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        presenceService = new PresenceService(
                stringRedisTemplate,
                presenceRealtimePublisher,
                conversationMemberRepository,
                userService);
    }

    @Test
    void markConnectedFirstSessionSetsOnlineAndPublishes() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-1";

        when(valueOperations.setIfAbsent(
                eq("presence:session:" + sessionId),
                eq(userId.toString()),
                eq(Duration.ofDays(1))))
                .thenReturn(true);
        when(valueOperations.get("presence:user-index:" + userId)).thenReturn("7");
        when(valueOperations.increment("presence:connections:" + userId)).thenReturn(1L);
        when(valueOperations.setBit("presence:online", 7L, true)).thenReturn(false);

        presenceService.markConnected(userId, sessionId);

        verify(presenceRealtimePublisher).publish(userId, true, null);
    }

    @Test
    void markConnectedSameSessionIsIdempotent() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-2";

        when(valueOperations.setIfAbsent(
                eq("presence:session:" + sessionId),
                eq(userId.toString()),
                eq(Duration.ofDays(1))))
                .thenReturn(false);
        when(valueOperations.get("presence:session:" + sessionId)).thenReturn(userId.toString());

        presenceService.markConnected(userId, sessionId);

        verify(stringRedisTemplate).expire("presence:session:" + sessionId, Duration.ofDays(1));
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void markDisconnectedWhenOtherSessionsRemainKeepsOnline() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-3";

        when(valueOperations.get("presence:session:" + sessionId)).thenReturn(userId.toString());
        when(valueOperations.decrement("presence:connections:" + userId)).thenReturn(1L);

        presenceService.markDisconnected(sessionId);

        verify(stringRedisTemplate).delete("presence:session:" + sessionId);
        verify(presenceRealtimePublisher, never()).publish(eq(userId), anyBoolean(), any());
    }

    @Test
    void markDisconnectedLastSessionSetsOfflineAndLastSeen() {
        UUID userId = UUID.randomUUID();
        String sessionId = "session-4";

        when(valueOperations.get("presence:session:" + sessionId)).thenReturn(userId.toString());
        when(valueOperations.decrement("presence:connections:" + userId)).thenReturn(0L);
        when(valueOperations.get("presence:user-index:" + userId)).thenReturn("9");
        when(valueOperations.setBit("presence:online", 9L, false)).thenReturn(true);

        presenceService.markDisconnected(sessionId);

        verify(stringRedisTemplate).delete("presence:connections:" + userId);
        verify(valueOperations).set(eq("presence:last-seen:" + userId), anyString());

        ArgumentCaptor<Instant> lastSeenCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(presenceRealtimePublisher).publish(eq(userId), eq(false), lastSeenCaptor.capture());
        assertThat(lastSeenCaptor.getValue()).isNotNull();
    }

    @Test
    void getPresenceWithoutIndexReturnsOffline() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.get("presence:user-index:" + userId)).thenReturn(null);
        when(valueOperations.get("presence:last-seen:" + userId))
                .thenReturn(String.valueOf(Instant.parse("2026-05-30T00:00:00Z").toEpochMilli()));

        PresenceItemResponse response = presenceService.getPresence(userId);

        assertThat(response.isOnline()).isFalse();
        assertThat(response.getLastSeenAt()).isEqualTo(Instant.parse("2026-05-30T00:00:00Z"));
    }

    @Test
    void getConversationPresenceRejectsNonMember() {
        UUID conversationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, currentUserId))
                .thenReturn(false);

        assertThatThrownBy(() -> presenceService.getConversationPresence(conversationId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getConversationPresenceReturnsMemberStates() {
        UUID conversationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID peerUserId = UUID.randomUUID();

        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, currentUserId))
                .thenReturn(true);

        ConversationMember self = new ConversationMember();
        self.setConversationId(conversationId);
        self.setUserId(currentUserId);
        ConversationMember peer = new ConversationMember();
        peer.setConversationId(conversationId);
        peer.setUserId(peerUserId);
        when(conversationMemberRepository.findByConversationId(conversationId))
                .thenReturn(List.of(self, peer));

        when(valueOperations.get("presence:user-index:" + currentUserId)).thenReturn("1");
        when(valueOperations.getBit("presence:online", 1L)).thenReturn(true);
        when(valueOperations.get("presence:user-index:" + peerUserId)).thenReturn("2");
        when(valueOperations.getBit("presence:online", 2L)).thenReturn(false);
        when(valueOperations.get("presence:last-seen:" + peerUserId))
                .thenReturn(String.valueOf(Instant.parse("2026-05-30T00:10:00Z").toEpochMilli()));

        ConversationPresenceResponse response = presenceService.getConversationPresence(conversationId);

        assertThat(response.getConversationId()).isEqualTo(conversationId);
        assertThat(response.getItems()).hasSize(2);
        PresenceItemResponse selfPresence = response.getItems().stream()
                .filter(item -> currentUserId.equals(item.getUserId()))
                .findFirst()
                .orElseThrow();
        PresenceItemResponse peerPresence = response.getItems().stream()
                .filter(item -> peerUserId.equals(item.getUserId()))
                .findFirst()
                .orElseThrow();

        assertThat(selfPresence.isOnline()).isTrue();
        assertThat(peerPresence.isOnline()).isFalse();
        assertThat(peerPresence.getLastSeenAt()).isEqualTo(Instant.parse("2026-05-30T00:10:00Z"));
    }
}

