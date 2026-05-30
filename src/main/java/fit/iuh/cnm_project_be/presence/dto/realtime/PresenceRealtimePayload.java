package fit.iuh.cnm_project_be.presence.dto.realtime;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PresenceRealtimePayload {
    private String eventType;
    private UUID userId;
    private boolean online;
    private Instant lastSeenAt;
    private Instant occurredAt;
}

