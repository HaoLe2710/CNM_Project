package fit.iuh.cnm_project_be.message_processing.config;

import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.impl.DisabledSpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.impl.DisabledTextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.impl.MockSpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.impl.MockTextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.impl.OpenAiSpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.impl.OpenAiTextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.impl.OpenAiProviderSupport;
import fit.iuh.cnm_project_be.message_processing.provider.impl.OpenRouterSpeechToTextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

@Configuration
public class MessageProcessingProviderConfig {

    @Bean
    public SpeechToTextProvider speechToTextProvider(
            MessageProcessingOpenAiProperties openAiProperties,
            MessageProcessingOpenRouterProperties openRouterProperties,
            ObjectMapper objectMapper,
            @Value("${app.message-processing.stt.provider:mock}") String providerCode) {
        String normalized = normalize(providerCode);
        return switch (normalized) {
            case "mock" -> new MockSpeechToTextProvider();
            case "openai" -> buildOpenAiSpeechProvider(openAiProperties, objectMapper);
            case "openrouter" -> buildOpenRouterSpeechProvider(openRouterProperties, objectMapper);
            case "disabled" -> new DisabledSpeechToTextProvider("Speech-to-text provider is not configured");
            default -> new DisabledSpeechToTextProvider("Unsupported STT provider: " + normalized);
        };
    }

    @Bean
    public TextToSpeechProvider textToSpeechProvider(
            MessageProcessingOpenAiProperties openAiProperties,
            ObjectMapper objectMapper,
            @Value("${app.message-processing.tts.provider:mock}") String providerCode) {
        String normalized = normalize(providerCode);
        return switch (normalized) {
            case "mock" -> new MockTextToSpeechProvider();
            case "openai" -> buildOpenAiTtsProvider(openAiProperties, objectMapper);
            case "disabled" -> new DisabledTextToSpeechProvider("Text-to-speech provider is not configured");
            default -> new DisabledTextToSpeechProvider("Unsupported TTS provider: " + normalized);
        };
    }

    private SpeechToTextProvider buildOpenAiSpeechProvider(
            MessageProcessingOpenAiProperties properties,
            ObjectMapper objectMapper) {
        if (OpenAiProviderSupport.trimToNull(properties.getApiKey()) == null) {
            return new DisabledSpeechToTextProvider("OpenAI STT provider requires app.message-processing.openai.api-key");
        }
        return new OpenAiSpeechToTextProvider(
                buildHttpClient(properties),
                objectMapper,
                properties.getApiKey(),
                properties.getBaseUrl(),
                normalizeModel(properties.getSttModel(), "gpt-4o-mini-transcribe"),
                resolveTimeout(properties));
    }

    private TextToSpeechProvider buildOpenAiTtsProvider(
            MessageProcessingOpenAiProperties properties,
            ObjectMapper objectMapper) {
        if (OpenAiProviderSupport.trimToNull(properties.getApiKey()) == null) {
            return new DisabledTextToSpeechProvider("OpenAI TTS provider requires app.message-processing.openai.api-key");
        }
        return new OpenAiTextToSpeechProvider(
                buildHttpClient(properties),
                objectMapper,
                properties.getApiKey(),
                properties.getBaseUrl(),
                normalizeModel(properties.getTtsModel(), "gpt-4o-mini-tts"),
                properties.getDefaultVoice(),
                resolveTimeout(properties));
    }

    private SpeechToTextProvider buildOpenRouterSpeechProvider(
            MessageProcessingOpenRouterProperties properties,
            ObjectMapper objectMapper) {
        if (OpenAiProviderSupport.trimToNull(properties.getApiKey()) == null) {
            return new DisabledSpeechToTextProvider("OpenRouter STT provider requires app.message-processing.openrouter.api-key");
        }
        return new OpenRouterSpeechToTextProvider(
                buildHttpClient(resolveTimeout(properties.getTimeoutSeconds())),
                objectMapper,
                properties.getApiKey(),
                properties.getBaseUrl(),
                normalizeModel(properties.getSttModel(), "openai/whisper-large-v3-turbo"),
                resolveTimeout(properties.getTimeoutSeconds()));
    }

    private HttpClient buildHttpClient(MessageProcessingOpenAiProperties properties) {
        return buildHttpClient(resolveTimeout(properties));
    }

    private HttpClient buildHttpClient(Duration timeout) {
        return HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    private Duration resolveTimeout(MessageProcessingOpenAiProperties properties) {
        int timeoutSeconds = Math.max(5, properties.getTimeoutSeconds());
        return Duration.ofSeconds(timeoutSeconds);
    }

    private Duration resolveTimeout(int timeoutSeconds) {
        return Duration.ofSeconds(Math.max(5, timeoutSeconds));
    }

    private String normalizeModel(String value, String fallback) {
        String normalized = OpenAiProviderSupport.trimToNull(value);
        return normalized != null ? normalized : fallback;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "mock";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
