package fit.iuh.cnm_project_be.message.dto;

import lombok.Data;

@Data
public class EditMessageRequest {

    private String content;

    private String originalLinkUrl;
}
