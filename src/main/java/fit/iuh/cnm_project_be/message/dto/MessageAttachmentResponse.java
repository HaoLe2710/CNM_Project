package fit.iuh.cnm_project_be.message.dto;

import fit.iuh.cnm_project_be.message.enums.MessageType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageAttachmentResponse {

    private Long id;
    private String url;
    private String storageKey;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private MessageType type;
}
