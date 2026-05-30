package fit.iuh.cnm_project_be.message_processing.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpeechToTextRequest {

    private Long messageId;
    private Long attachmentId;
    private String fileUrl;
    private String storageKey;
    private String fileName;
    private String mimeType;
    private String audioFormat;
    private Long durationMs;
    private String language;
    private byte[] audioBytes;
}
