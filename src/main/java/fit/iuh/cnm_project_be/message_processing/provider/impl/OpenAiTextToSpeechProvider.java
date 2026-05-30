package fit.iuh.cnm_project_be.message_processing.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechRequest;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechResult;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenAiTextToSpeechProvider implements TextToSpeechProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String defaultVoice;
    private final Duration timeout;

    public OpenAiTextToSpeechProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl,
            String model,
            String defaultVoice,
            Duration timeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.defaultVoice = defaultVoice;
        this.timeout = timeout;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public TextToSpeechResult synthesize(TextToSpeechRequest request) {
        if (request == null || OpenAiProviderSupport.trimToNull(request.getText()) == null) {
            throw new BusinessException("Text-to-speech request is missing text");
        }
        String normalizedApiKey = OpenAiProviderSupport.trimToNull(apiKey);
        if (normalizedApiKey == null) {
            throw new BusinessException("OpenAI API key is not configured");
        }

        String voice = OpenAiProviderSupport.trimToNull(request.getVoice());
        if (voice == null) {
            voice = OpenAiProviderSupport.trimToNull(defaultVoice);
        }
        if (voice == null) {
            voice = "alloy";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", request.getText().trim());
        payload.put("voice", voice);
        payload.put("format", "mp3");

        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder(OpenAiProviderSupport.resolveUri(baseUrl, "/v1/audio/speech"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + normalizedApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                String rawBody = response.body() != null ? new String(response.body()) : null;
                throw new BusinessException(OpenAiProviderSupport.resolveErrorMessage(
                        objectMapper,
                        response.statusCode(),
                        rawBody,
                        "Text-to-speech request failed"));
            }

            byte[] audioBytes = response.body();
            if (audioBytes == null || audioBytes.length == 0) {
                throw new BusinessException("Text-to-speech provider returned empty audio");
            }

            return TextToSpeechResult.builder()
                    .audioBytes(audioBytes)
                    .mimeType("audio/mpeg")
                    .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Text-to-speech request was interrupted");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Text-to-speech provider request failed");
        }
    }
}
