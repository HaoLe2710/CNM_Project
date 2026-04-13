package fit.iuh.cnm_project_be.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingRealtimePayload {
    private UUID conversationId;
    private UUID userId;
    private UUID senderId;
    private Boolean isTyping;
    private String displayName;

    @JsonProperty("typing")
    public Boolean getTyping() {
        return isTyping;
    }
}
