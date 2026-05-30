package fit.iuh.cnm_project_be.message_processing.dto.request;

import lombok.Data;

@Data
public class CreateTextToSpeechJobRequest {

    private String language;
    private String voice;
    private Boolean forceRefresh;
}
