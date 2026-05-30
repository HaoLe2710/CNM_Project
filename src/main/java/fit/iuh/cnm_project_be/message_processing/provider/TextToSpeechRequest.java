package fit.iuh.cnm_project_be.message_processing.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextToSpeechRequest {

    private Long messageId;
    private String text;
    private String language;
    private String voice;
}
