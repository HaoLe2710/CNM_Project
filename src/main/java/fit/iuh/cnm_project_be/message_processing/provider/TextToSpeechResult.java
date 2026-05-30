package fit.iuh.cnm_project_be.message_processing.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextToSpeechResult {

    private byte[] audioBytes;
    private String mimeType;
    private Long durationMs;
}
