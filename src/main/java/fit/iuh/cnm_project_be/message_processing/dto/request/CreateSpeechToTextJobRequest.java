package fit.iuh.cnm_project_be.message_processing.dto.request;

import lombok.Data;

@Data
public class CreateSpeechToTextJobRequest {

    private Long attachmentId;
    private String language;
    private Boolean forceRefresh;
}
