package fit.iuh.cnm_project_be.presence.service;

import fit.iuh.cnm_project_be.presence.dto.realtime.PresenceRealtimePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PresenceRealtimePublisher {

    public static final String PRESENCE_CHANGED = "PRESENCE_CHANGED";
    private static final String PRESENCE_TOPIC = "/topic/presence";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(UUID userId, boolean online, Instant lastSeenAt) {
        messagingTemplate.convertAndSend(
                PRESENCE_TOPIC,
                PresenceRealtimePayload.builder()
                        .eventType(PRESENCE_CHANGED)
                        .userId(userId)
                        .online(online)
                        .lastSeenAt(lastSeenAt)
                        .occurredAt(Instant.now())
                        .build());
    }
}

