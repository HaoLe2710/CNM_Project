package fit.iuh.cnm_project_be.room.dto;

import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private UUID id;
    private String name;
    private String avatarUrl;
    private String type;
    private String lastMessage;
    private Instant lastMessageTime;
    private Long unreadCount;
    private boolean muted;
    private boolean archived;
    private boolean pinned;
    private ConversationNotificationLevel notificationLevel;
    private String customName;
    private String displayName;
}
