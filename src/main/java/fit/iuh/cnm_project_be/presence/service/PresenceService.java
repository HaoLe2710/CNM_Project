package fit.iuh.cnm_project_be.presence.service;

import fit.iuh.cnm_project_be.common.exception.ForbiddenException;
import fit.iuh.cnm_project_be.presence.dto.response.BatchPresenceResponse;
import fit.iuh.cnm_project_be.presence.dto.response.ConversationPresenceResponse;
import fit.iuh.cnm_project_be.presence.dto.response.PresenceItemResponse;
import fit.iuh.cnm_project_be.room.entity.ConversationMember;
import fit.iuh.cnm_project_be.room.repository.ConversationMemberRepository;
import fit.iuh.cnm_project_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    static final String PRESENCE_ONLINE_BITMAP_KEY = "presence:online";
    static final String PRESENCE_NEXT_INDEX_KEY = "presence:next-index";
    static final String PRESENCE_USER_INDEX_PREFIX = "presence:user-index:";
    static final String PRESENCE_INDEX_USER_PREFIX = "presence:index-user:";
    static final String PRESENCE_CONNECTIONS_PREFIX = "presence:connections:";
    static final String PRESENCE_SESSION_PREFIX = "presence:session:";
    static final String PRESENCE_LAST_SEEN_PREFIX = "presence:last-seen:";
    static final String PRESENCE_INDEX_LOCK_PREFIX = "presence:index-lock:";

    private static final Duration SESSION_TTL = Duration.ofDays(1);
    private static final Duration INDEX_LOCK_TTL = Duration.ofSeconds(5);
    private static final int INDEX_LOCK_SPIN_RETRY = 6;
    private static final long INDEX_LOCK_SPIN_DELAY_MS = 25L;

    private final StringRedisTemplate stringRedisTemplate;
    private final PresenceRealtimePublisher presenceRealtimePublisher;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserService userService;

    public void markConnected(UUID userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }

        String sessionKey = sessionKey(sessionId);
        String serializedUserId = userId.toString();
        Boolean firstSeenSession = stringRedisTemplate.opsForValue()
                .setIfAbsent(sessionKey, serializedUserId, SESSION_TTL);

        if (!Boolean.TRUE.equals(firstSeenSession)) {
            String existing = stringRedisTemplate.opsForValue().get(sessionKey);
            if (serializedUserId.equals(existing)) {
                stringRedisTemplate.expire(sessionKey, SESSION_TTL);
            }
            return;
        }

        long index = ensureUserIndex(userId);
        Long nextConnectionCount = stringRedisTemplate.opsForValue().increment(connectionsKey(userId));
        if (nextConnectionCount == null) {
            return;
        }

        if (nextConnectionCount == 1L) {
            Boolean previousBit = stringRedisTemplate.opsForValue()
                    .setBit(PRESENCE_ONLINE_BITMAP_KEY, index, true);
            if (!Boolean.TRUE.equals(previousBit)) {
                presenceRealtimePublisher.publish(userId, true, null);
            }
        }
    }

    public void markDisconnected(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        String sessionKey = sessionKey(sessionId);
        String userIdValue = stringRedisTemplate.opsForValue().get(sessionKey);
        if (userIdValue == null || userIdValue.isBlank()) {
            return;
        }

        stringRedisTemplate.delete(sessionKey);
        UUID userId = parseUuid(userIdValue);
        if (userId == null) {
            return;
        }

        Long remainingConnections = stringRedisTemplate.opsForValue().decrement(connectionsKey(userId));
        long remaining = remainingConnections == null ? 0L : remainingConnections;
        if (remaining > 0L) {
            return;
        }

        stringRedisTemplate.delete(connectionsKey(userId));
        Instant now = Instant.now();
        Long index = getExistingUserIndex(userId);
        boolean wasOnline = false;
        if (index != null) {
            Boolean previousBit = stringRedisTemplate.opsForValue()
                    .setBit(PRESENCE_ONLINE_BITMAP_KEY, index, false);
            wasOnline = Boolean.TRUE.equals(previousBit);
        }

        stringRedisTemplate.opsForValue().set(lastSeenKey(userId), String.valueOf(now.toEpochMilli()));
        if (wasOnline || index == null) {
            presenceRealtimePublisher.publish(userId, false, now);
        }
    }

    public PresenceItemResponse getPresence(UUID userId) {
        if (userId == null) {
            return PresenceItemResponse.builder()
                    .userId(null)
                    .online(false)
                    .lastSeenAt(null)
                    .build();
        }

        boolean online = isOnline(userId);
        Instant lastSeenAt = online ? null : readLastSeen(userId);

        return PresenceItemResponse.builder()
                .userId(userId)
                .online(online)
                .lastSeenAt(lastSeenAt)
                .build();
    }

    public BatchPresenceResponse getBatchPresence(Collection<UUID> userIds) {
        List<PresenceItemResponse> items = new ArrayList<>();
        if (userIds == null || userIds.isEmpty()) {
            return BatchPresenceResponse.builder()
                    .items(items)
                    .build();
        }

        Set<UUID> dedupedIds = new LinkedHashSet<>();
        for (UUID userId : userIds) {
            if (userId != null) {
                dedupedIds.add(userId);
            }
        }

        for (UUID userId : dedupedIds) {
            items.add(getPresence(userId));
        }

        return BatchPresenceResponse.builder()
                .items(items)
                .build();
    }

    public ConversationPresenceResponse getConversationPresence(UUID conversationId) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, currentUserId)) {
            throw new ForbiddenException("User does not belong to this conversation");
        }

        List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversationId);
        List<UUID> memberIds = members.stream()
                .map(ConversationMember::getUserId)
                .filter(userId -> userId != null)
                .toList();

        return ConversationPresenceResponse.builder()
                .conversationId(conversationId)
                .items(getBatchPresence(memberIds).getItems())
                .build();
    }

    boolean isOnline(UUID userId) {
        Long index = getExistingUserIndex(userId);
        if (index == null) {
            return false;
        }
        Boolean bitValue = stringRedisTemplate.opsForValue()
                .getBit(PRESENCE_ONLINE_BITMAP_KEY, index);
        return Boolean.TRUE.equals(bitValue);
    }

    Instant readLastSeen(UUID userId) {
        String rawValue = stringRedisTemplate.opsForValue().get(lastSeenKey(userId));
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(rawValue));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    Long getExistingUserIndex(UUID userId) {
        String rawValue = stringRedisTemplate.opsForValue().get(userIndexKey(userId));
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ex) {
            log.warn("[Presence] Invalid user index mapping userId={} value={}", userId, rawValue);
            return null;
        }
    }

    long ensureUserIndex(UUID userId) {
        Long existingIndex = getExistingUserIndex(userId);
        if (existingIndex != null) {
            return existingIndex;
        }

        String lockKey = indexLockKey(userId);
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, INDEX_LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                Long doubleCheckIndex = getExistingUserIndex(userId);
                if (doubleCheckIndex != null) {
                    return doubleCheckIndex;
                }

                Long allocatedIndex = stringRedisTemplate.opsForValue().increment(PRESENCE_NEXT_INDEX_KEY);
                if (allocatedIndex == null) {
                    throw new IllegalStateException("Cannot allocate presence index");
                }

                stringRedisTemplate.opsForValue().set(userIndexKey(userId), String.valueOf(allocatedIndex));
                stringRedisTemplate.opsForValue().set(indexUserKey(allocatedIndex), userId.toString());
                return allocatedIndex;
            } finally {
                String currentLockToken = stringRedisTemplate.opsForValue().get(lockKey);
                if (lockToken.equals(currentLockToken)) {
                    stringRedisTemplate.delete(lockKey);
                }
            }
        }

        for (int retry = 0; retry < INDEX_LOCK_SPIN_RETRY; retry++) {
            Long index = getExistingUserIndex(userId);
            if (index != null) {
                return index;
            }
            sleepQuietly(INDEX_LOCK_SPIN_DELAY_MS);
        }

        Long fallbackIndex = stringRedisTemplate.opsForValue().increment(PRESENCE_NEXT_INDEX_KEY);
        if (fallbackIndex == null) {
            throw new IllegalStateException("Cannot allocate fallback presence index");
        }
        stringRedisTemplate.opsForValue().set(userIndexKey(userId), String.valueOf(fallbackIndex));
        stringRedisTemplate.opsForValue().set(indexUserKey(fallbackIndex), userId.toString());
        return fallbackIndex;
    }

    private void sleepQuietly(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private UUID parseUuid(String rawValue) {
        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("[Presence] Invalid UUID in session mapping value={}", rawValue);
            return null;
        }
    }

    private String userIndexKey(UUID userId) {
        return PRESENCE_USER_INDEX_PREFIX + userId;
    }

    private String indexUserKey(long index) {
        return PRESENCE_INDEX_USER_PREFIX + index;
    }

    private String connectionsKey(UUID userId) {
        return PRESENCE_CONNECTIONS_PREFIX + userId;
    }

    private String sessionKey(String sessionId) {
        return PRESENCE_SESSION_PREFIX + sessionId;
    }

    private String lastSeenKey(UUID userId) {
        return PRESENCE_LAST_SEEN_PREFIX + userId;
    }

    private String indexLockKey(UUID userId) {
        return PRESENCE_INDEX_LOCK_PREFIX + userId;
    }
}

