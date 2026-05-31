package fit.iuh.cnm_project_be.message_processing.provider;

import lombok.Getter;

@Getter
public class SpeechToTextProviderException extends RuntimeException {

    private final boolean retryable;
    private final Integer statusCode;
    private final String category;

    public SpeechToTextProviderException(String message, boolean retryable, Integer statusCode, String category) {
        super(message);
        this.retryable = retryable;
        this.statusCode = statusCode;
        this.category = category;
    }

    public SpeechToTextProviderException(String message, boolean retryable, String category) {
        this(message, retryable, null, category);
    }
}
