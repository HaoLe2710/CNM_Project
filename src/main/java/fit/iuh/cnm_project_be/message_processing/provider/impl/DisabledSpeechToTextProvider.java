package fit.iuh.cnm_project_be.message_processing.provider.impl;

import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextRequest;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextResult;

public class DisabledSpeechToTextProvider implements SpeechToTextProvider {

    private final String reason;

    public DisabledSpeechToTextProvider(String reason) {
        this.reason = reason;
    }

    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public SpeechToTextResult transcribe(SpeechToTextRequest request) {
        throw new BusinessException(reason != null && !reason.isBlank()
                ? reason
                : "Speech-to-text provider is not configured");
    }
}
