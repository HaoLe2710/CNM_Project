package fit.iuh.cnm_project_be.message_processing.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateDictationSpeechToTextRequest {

    private UUID conversationId;
    private String language;
    private String audioFormat;
    private Long durationMs;
}
