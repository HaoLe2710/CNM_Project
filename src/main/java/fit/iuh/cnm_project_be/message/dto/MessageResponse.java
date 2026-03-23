package fit.iuh.cnm_project_be.message.dto;

import fit.iuh.cnm_project_be.message.enums.MessageType;
import fit.iuh.cnm_project_be.message.enums.MessageReactionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MessageResponse {

    private Long id;
    private UUID conversationId;
    private UUID senderId;
    private String content;
    private MessageType type;
    private ReplyInfo replyTo;
    private List<MessageAttachmentResponse> attachments;
    private List<MessageReactionSummary> reactions;
    private MessageReactionType myReaction;
    private Boolean seen;
    private Instant createdAt;
    private Instant editedAt;
}
