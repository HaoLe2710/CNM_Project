package fit.iuh.cnm_project_be.message_processing.enums;

import java.util.Locale;
import java.util.Optional;

public enum MessageProcessingJobType {
    STT,
    TTS;

    public static Optional<MessageProcessingJobType> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MessageProcessingJobType.valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
