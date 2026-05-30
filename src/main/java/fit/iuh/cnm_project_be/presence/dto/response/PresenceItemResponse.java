package fit.iuh.cnm_project_be.presence.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PresenceItemResponse {
    private UUID userId;
    private boolean online;
    private Instant lastSeenAt;
}

