package fit.iuh.cnm_project_be.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class RealtimeEvent<T> {

    private RealtimeEventType type;
    private T payload;
    private Instant occurredAt;

    public static <T> RealtimeEvent<T> of(RealtimeEventType type, T payload) {
        return new RealtimeEvent<>(type, payload, Instant.now());
    }
}
