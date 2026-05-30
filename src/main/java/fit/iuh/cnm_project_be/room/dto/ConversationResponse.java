package fit.iuh.cnm_project_be.room.dto;

import fit.iuh.cnm_project_be.room.enums.ConversationNotificationLevel;
import fit.iuh.cnm_project_be.room.enums.ConversationBackgroundType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
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
    private ConversationBackgroundType backgroundType;
    private String backgroundColor;
    private String backgroundImageUrl;
    private String groupLabel;
    private String groupLabelDisplayName;
    private String groupLabelColor;
    private String displayName;
    private UUID peerUserId;
    private String peerDisplayName;
    private String peerAvatarUrl;
    private List<ConversationMemberResponse> members;

}
