package fit.iuh.cnm_project_be.message_processing.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextProvider;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextRequest;
import fit.iuh.cnm_project_be.message_processing.provider.SpeechToTextResult;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OpenAiSpeechToTextProvider implements SpeechToTextProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public OpenAiSpeechToTextProvider(
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
        return "openai";
    }

    @Override
    public SpeechToTextResult transcribe(SpeechToTextRequest request) {
        if (request == null || request.getAudioBytes() == null || request.getAudioBytes().length == 0) {
            throw new BusinessException("Speech-to-text request is missing audio data");
        }
        String normalizedApiKey = OpenAiProviderSupport.trimToNull(apiKey);
        if (normalizedApiKey == null) {
            throw new BusinessException("OpenAI API key is not configured");
        }

        String extension = OpenAiProviderSupport.sanitizeAudioExtension(
                request.getFileName(),
                request.getAudioFormat(),
                request.getMimeType());
        OpenAiProviderSupport.validateSttAudioFormat(request.getFileName(), request.getAudioFormat(), request.getMimeType());

        String normalizedMimeType = OpenAiProviderSupport.trimToNull(request.getMimeType());
        if (normalizedMimeType == null) {
            normalizedMimeType = "audio/" + ("m4a".equals(extension) ? "mp4" : extension);
        }

        String normalizedFileName = OpenAiProviderSupport.trimToNull(request.getFileName());
        if (normalizedFileName == null) {
            normalizedFileName = "voice-message." + extension;
        }

        String normalizedLanguage = OpenAiProviderSupport.trimToNull(request.getLanguage());
        if (normalizedLanguage == null) {
            normalizedLanguage = "vi";
        }

        String boundary = OpenAiProviderSupport.newBoundary();
        OpenAiProviderSupport.MultipartPayload payload = OpenAiProviderSupport.buildMultipartPayload(
                boundary,
                OpenAiProviderSupport.listOf(
                        new OpenAiProviderSupport.FormField("model", model),
                        new OpenAiProviderSupport.FormField("language", normalizedLanguage),
                        new OpenAiProviderSupport.FormField("response_format", "json")),
                "file",
                normalizedFileName,
                normalizedMimeType,
                request.getAudioBytes());

        HttpRequest httpRequest = HttpRequest.newBuilder(OpenAiProviderSupport.resolveUri(baseUrl, "/v1/audio/transcriptions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + normalizedApiKey)
                .header("Content-Type", payload.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload.body()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new BusinessException(OpenAiProviderSupport.resolveErrorMessage(
                        objectMapper,
                        response.statusCode(),
                        response.body(),
                        "Speech-to-text request failed"));
            }

            String transcript = parseTranscript(response.body());
            if (OpenAiProviderSupport.trimToNull(transcript) == null) {
                throw new BusinessException("Speech-to-text provider returned an empty transcript");
            }

            return SpeechToTextResult.builder()
                    .transcript(transcript.trim())
                    .language(normalizedLanguage)
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

    private String parseTranscript(String rawBody) {
        String normalized = OpenAiProviderSupport.trimToNull(rawBody);
        if (normalized == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            String text = OpenAiProviderSupport.trimToNull(root.path("text").asText(null));
            if (text != null) {
                return text;
            }
        } catch (Exception ignored) {
            // fallback to raw response
        }
        return normalized;
    }
}
