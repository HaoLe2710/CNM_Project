package fit.iuh.cnm_project_be.message_processing.enums;

import java.util.Locale;
import java.util.Optional;

public enum MessageProcessingJobScope {
    MESSAGE,
    DICTATION;

    public static Optional<MessageProcessingJobScope> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MessageProcessingJobScope.valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
