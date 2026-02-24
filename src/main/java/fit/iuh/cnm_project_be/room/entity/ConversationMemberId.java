package fit.iuh.cnm_project_be.room.entity;

import java.io.Serializable;
import java.util.UUID;

public class ConversationMemberId implements Serializable {
    private UUID conversationId;
    private UUID userId;
}