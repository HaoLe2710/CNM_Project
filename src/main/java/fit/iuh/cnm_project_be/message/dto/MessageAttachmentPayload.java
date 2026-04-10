package fit.iuh.cnm_project_be.message.dto;

import fit.iuh.cnm_project_be.message.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageAttachmentPayload {

    @NotBlank
    private String url;

    private String storageKey;

    @NotBlank
    private String fileName;

    private String contentType;

    @NotNull
    private Long fileSize;

    @NotNull
    private MessageType type;
}
