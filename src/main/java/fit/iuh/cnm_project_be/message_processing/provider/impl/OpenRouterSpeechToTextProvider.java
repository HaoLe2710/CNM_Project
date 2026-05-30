package fit.iuh.cnm_project_be.message_processing.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProviderException;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextRequest;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
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
            throw new SpeechToTextProviderException(
                    "Speech-to-text request is missing audio data",
                    false,
                    "VALIDATION");
        }

        String normalizedApiKey = OpenAiProviderSupport.trimToNull(apiKey);
        if (normalizedApiKey == null) {
            throw new SpeechToTextProviderException(
                    "OpenRouter STT provider requires app.message-processing.openrouter.api-key",
                    false,
                    "AUTH_CONFIG");
        }

        String format = normalizeAudioFormat(request);
        try {
            OpenAiProviderSupport.validateSttAudioFormat(request.getFileName(), format, request.getMimeType());
        } catch (BusinessException ex) {
            throw new SpeechToTextProviderException(
                    resolveSafeValidationMessage(ex.getMessage()),
                    false,
                    400,
                    "UNSUPPORTED_FORMAT");
        }
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
            throw new SpeechToTextProviderException(
                    "Unable to serialize OpenRouter STT payload",
                    false,
                    "SERIALIZE");
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
                throw resolveOpenRouterError(response.statusCode(), response.body(), format);
            }

            String transcript = parseTranscript(response.body());
            if (OpenAiProviderSupport.trimToNull(transcript) == null) {
                throw new SpeechToTextProviderException(
                        "Speech-to-text provider returned an empty transcript",
                        false,
                        response.statusCode(),
                        "EMPTY_RESULT");
            }

            return SpeechToTextResult.builder()
                    .transcript(transcript.trim())
                    .language(language)
                    .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SpeechToTextProviderException(
                    "Speech-to-text request was interrupted",
                    true,
                    "INTERRUPTED");
        } catch (HttpTimeoutException ex) {
            throw new SpeechToTextProviderException(
                    "Speech-to-text provider timed out",
                    true,
                    "TIMEOUT");
        } catch (IOException ex) {
            throw new SpeechToTextProviderException(
                    "Speech-to-text provider network failure",
                    true,
                    "NETWORK");
        } catch (SpeechToTextProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SpeechToTextProviderException(
                    "Speech-to-text provider request failed",
                    true,
                    "UNEXPECTED");
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
            String output = OpenAiProviderSupport.trimToNull(root.path("output_text").asText(null));
            if (output != null) {
                return output;
            }
            return null;
        } catch (Exception ignored) {
            // fallback below
        }
        return normalizedBody;
    }

    private URI resolveEndpointUri(String configuredBaseUrl) {
        String normalizedBaseUrl = OpenAiProviderSupport.trimToNull(configuredBaseUrl);
        if (normalizedBaseUrl == null) {
            throw new SpeechToTextProviderException(
                    "OpenRouter base URL is not configured",
                    false,
                    "CONFIG");
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

    private SpeechToTextProviderException resolveOpenRouterError(int statusCode, String rawBody, String audioFormat) {
        String message = extractSafeErrorMessage(rawBody);
        boolean retryable = isRetryableStatus(statusCode);
        String category = resolveErrorCategory(statusCode);
        String baseMessage = message != null
                ? "OpenRouter STT error (" + statusCode + "): " + message
                : "OpenRouter STT request failed (" + statusCode + ")";

        log.warn(
                "[MessageProcessing][OpenRouterSTT] statusCode={} retryable={} category={} model={} audioFormat={}",
                statusCode,
                retryable,
                category,
                model,
                audioFormat);

        return new SpeechToTextProviderException(baseMessage, retryable, statusCode, category);
    }

    private String extractSafeErrorMessage(String rawBody) {
        String message = OpenAiProviderSupport.trimToNull(rawBody);
        if (message == null) {
            return null;
        }

        String resolved = null;
        try {
            JsonNode root = objectMapper.readTree(message);
            resolved = OpenAiProviderSupport.trimToNull(root.path("error").path("message").asText(null));
            if (resolved == null) {
                resolved = OpenAiProviderSupport.trimToNull(root.path("message").asText(null));
            }
        } catch (Exception ignored) {
            resolved = message;
        }

        if (resolved == null) {
            return null;
        }

        String singleLine = resolved.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (singleLine.length() > 280) {
            return singleLine.substring(0, 280) + "...";
        }
        return singleLine;
    }

    private String resolveSafeValidationMessage(String rawMessage) {
        String message = OpenAiProviderSupport.trimToNull(rawMessage);
        if (message == null) {
            return "Định dạng âm thanh chưa được hỗ trợ.";
        }
        if (message.length() > 280) {
            return message.substring(0, 280) + "...";
        }
        return message;
    }

    private boolean isRetryableStatus(int statusCode) {
        if (statusCode == 429 || statusCode == 408) {
            return true;
        }
        return statusCode >= 500;
    }

    private String resolveErrorCategory(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "AUTH";
        }
        if (statusCode == 429) {
            return "RATE_LIMIT";
        }
        if (statusCode == 408) {
            return "TIMEOUT";
        }
        if (statusCode >= 500) {
            return "SERVER";
        }
        if (statusCode >= 400) {
            return "BAD_REQUEST";
        }
        return "UNKNOWN";
    }
}
