package fit.iuh.cnm_project_be.message.dto;

import fit.iuh.cnm_project_be.message.enums.MessageReactionType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MessageReactionEventPayload {

    private Long messageId;
    private UUID conversationId;
    private UUID actorUserId;
    private MessageReactionType actorReaction;
    private List<MessageReactionSummary> summary;
}
