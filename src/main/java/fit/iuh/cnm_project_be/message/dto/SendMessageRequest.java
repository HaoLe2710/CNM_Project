package fit.iuh.cnm_project_be.message.dto;

import fit.iuh.cnm_project_be.message.enums.MessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SendMessageRequest {

    @NotNull
    private UUID conversationId;

    private String content;

    private String originalLinkUrl;

    private Long replyToMessageId;

    private MessageType messageType;

    @Valid
    private List<MessageAttachmentPayload> attachments;

    @Valid
    private List<MessageMentionPayload> mentions;
}
