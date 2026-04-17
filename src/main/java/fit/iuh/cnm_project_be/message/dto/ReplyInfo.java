package fit.iuh.cnm_project_be.message.dto;

import fit.iuh.cnm_project_be.message.enums.MessageType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ReplyInfo {

    private Long messageId;
    private UUID senderId;
    private String senderDisplayName;
    private String senderAvatarUrl;
    private String contentPreview;
    private MessageType type;
}
