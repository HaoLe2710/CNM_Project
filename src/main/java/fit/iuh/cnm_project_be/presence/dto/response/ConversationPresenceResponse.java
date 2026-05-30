package fit.iuh.cnm_project_be.presence.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ConversationPresenceResponse {
    private UUID conversationId;
    private List<PresenceItemResponse> items;
}

