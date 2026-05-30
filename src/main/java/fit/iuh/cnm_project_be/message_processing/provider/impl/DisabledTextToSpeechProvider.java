package fit.iuh.cnm_project_be.message_processing.provider.impl;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechRequest;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechResult;

public class DisabledTextToSpeechProvider implements TextToSpeechProvider {

    private final String reason;

    public DisabledTextToSpeechProvider(String reason) {
        this.reason = reason;
    }

    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public TextToSpeechResult synthesize(TextToSpeechRequest request) {
        throw new BusinessException(reason != null && !reason.isBlank()
                ? reason
                : "Text-to-speech provider is not configured");
    }
}
