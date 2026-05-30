package fit.iuh.cnm_project_be.message_processing.provider.impl;

import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextRequest;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextResult;

import java.util.Locale;

public class MockSpeechToTextProvider implements SpeechToTextProvider {

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public SpeechToTextResult transcribe(SpeechToTextRequest request) {
        String language = normalizeLanguage(request != null ? request.getLanguage() : null);
        long durationMs = request != null && request.getDurationMs() != null
                ? Math.max(request.getDurationMs(), 0L)
                : 0L;
        long seconds = Math.round(durationMs / 1000.0);
        String transcript = seconds > 0
                ? "Bản chuyển văn bản (mô phỏng): tin nhắn thoại dài khoảng " + seconds + " giây."
                : "Bản chuyển văn bản (mô phỏng): nội dung thoại đã được nhận dạng.";

        return SpeechToTextResult.builder()
                .transcript(transcript)
                .language(language)
                .confidence(0.76D)
                .build();
    }

    private String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            return "vi";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
