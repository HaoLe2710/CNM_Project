package fit.iuh.cnm_project_be.message_processing.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextRequest;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class OpenRouterSpeechToTextProvider implements SpeechToTextProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public OpenRouterSpeechToTextProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl,
            String model,
            Duration timeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public String providerName() {
        return "openrouter";
    }

    @Override
    public SpeechToTextResult transcribe(SpeechToTextRequest request) {
        if (request == null || request.getAudioBytes() == null || request.getAudioBytes().length == 0) {
            throw new BusinessException("Speech-to-text request is missing audio data");
        }

        String normalizedApiKey = OpenAiProviderSupport.trimToNull(apiKey);
        if (normalizedApiKey == null) {
            throw new BusinessException("OpenRouter STT provider requires app.message-processing.openrouter.api-key");
        }

        String format = normalizeAudioFormat(request);
        String language = OpenAiProviderSupport.trimToNull(request.getLanguage());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        Map<String, Object> inputAudio = new LinkedHashMap<>();
        inputAudio.put("data", Base64.getEncoder().encodeToString(request.getAudioBytes()));
        inputAudio.put("format", format);
        payload.put("input_audio", inputAudio);
        if (language != null) {
            payload.put("language", language);
        }

        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException("Unable to serialize OpenRouter STT payload");
        }

        URI endpointUri = resolveEndpointUri(baseUrl);
        HttpRequest httpRequest = HttpRequest.newBuilder(endpointUri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + normalizedApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(rawPayload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new BusinessException(resolveOpenRouterError(response.statusCode(), response.body()));
            }

            String transcript = parseTranscript(response.body());
            if (OpenAiProviderSupport.trimToNull(transcript) == null) {
                throw new BusinessException("Speech-to-text provider returned an empty transcript");
            }

            return SpeechToTextResult.builder()
                    .transcript(transcript.trim())
                    .language(language)
                    .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Speech-to-text request was interrupted");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Speech-to-text provider request failed");
        }
    }

    private String normalizeAudioFormat(SpeechToTextRequest request) {
        String normalizedMimeType = OpenAiProviderSupport.trimToNull(request.getMimeType());
        String normalizedAudioFormat = OpenAiProviderSupport.trimToNull(request.getAudioFormat());
        String extension = OpenAiProviderSupport.sanitizeAudioExtension(
                request.getFileName(),
                normalizedAudioFormat,
                normalizedMimeType);
        String normalized = extension.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mpeg", "mpga" -> "mp3";
            default -> normalized;
        };
    }

    private String parseTranscript(String rawBody) {
        String normalizedBody = OpenAiProviderSupport.trimToNull(rawBody);
        if (normalizedBody == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(normalizedBody);
            String text = OpenAiProviderSupport.trimToNull(root.path("text").asText(null));
            if (text != null) {
                return text;
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return normalizedBody;
    }

    private URI resolveEndpointUri(String configuredBaseUrl) {
        String normalizedBaseUrl = OpenAiProviderSupport.trimToNull(configuredBaseUrl);
        if (normalizedBaseUrl == null) {
            throw new BusinessException("OpenRouter base URL is not configured");
        }
        String withoutTrailingSlash = normalizedBaseUrl.replaceAll("/+$", "");
        if (withoutTrailingSlash.endsWith("/audio/transcriptions")) {
            return URI.create(withoutTrailingSlash);
        }
        if (withoutTrailingSlash.endsWith("/api/v1")) {
            return URI.create(withoutTrailingSlash + "/audio/transcriptions");
        }
        return URI.create(withoutTrailingSlash + "/api/v1/audio/transcriptions");
    }

    private String resolveOpenRouterError(int statusCode, String rawBody) {
        String message = OpenAiProviderSupport.trimToNull(rawBody);
        if (message != null) {
            try {
                JsonNode root = objectMapper.readTree(message);
                String nested = OpenAiProviderSupport.trimToNull(root.path("error").path("message").asText(null));
                if (nested != null) {
                    return "OpenRouter STT error (" + statusCode + "): " + nested;
                }
            } catch (Exception ignored) {
                // ignore JSON parsing failures
            }
            return "OpenRouter STT error (" + statusCode + "): " + message;
        }
        return "OpenRouter STT request failed (" + statusCode + ")";
    }
}
