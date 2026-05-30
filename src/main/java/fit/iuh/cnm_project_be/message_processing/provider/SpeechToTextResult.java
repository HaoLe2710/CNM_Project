package fit.iuh.cnm_project_be.message_processing.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpeechToTextResult {

    private String transcript;
    private String language;
    private Double confidence;
}
