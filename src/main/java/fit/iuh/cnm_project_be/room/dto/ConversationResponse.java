package fit.iuh.cnm_project_be.room.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private UUID id;
    private String name;
    private String type;
    private String lastMessage;
    private Instant lastMessageTime;
    private Long unreadCount;
}