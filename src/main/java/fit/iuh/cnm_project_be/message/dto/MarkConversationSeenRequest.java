package fit.iuh.cnm_project_be.message.dto;

import lombok.Data;

@Data
public class MarkConversationSeenRequest {
    private Long lastReadMessageId;
}
