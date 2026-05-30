package fit.iuh.cnm_project_be.message_processing.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.common.exception.BusinessException;
import fit.iuh.cnm_project_be.message_processing.provider.impl.OpenRouterSpeechToTextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenRouterSpeechToTextProviderTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void transcribeSuccessParsesTextAndUsesJsonEndpoint() throws Exception {
        OpenRouterSpeechToTextProvider provider = new OpenRouterSpeechToTextProvider(
                httpClient,
                objectMapper,
                "sk-or-v1-test",
                "https://openrouter.ai/api/v1",
                "openai/whisper-large-v3-turbo",
                Duration.ofSeconds(30));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"text\":\"xin chao\"}");

        SpeechToTextResult result = provider.transcribe(SpeechToTextRequest.builder()
                .audioBytes(new byte[] {1, 2, 3, 4})
                .mimeType("audio/webm")
                .audioFormat("webm")
                .language("vi")
                .build());

        assertThat(result.getTranscript()).isEqualTo("xin chao");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.uri().toString()).isEqualTo("https://openrouter.ai/api/v1/audio/transcriptions");
        assertThat(sentRequest.headers().firstValue("Authorization")).contains("Bearer sk-or-v1-test");
        assertThat(sentRequest.headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    void transcribeMissingApiKeyRejected() {
        OpenRouterSpeechToTextProvider provider = new OpenRouterSpeechToTextProvider(
                httpClient,
                objectMapper,
                "",
                "https://openrouter.ai/api/v1",
                "openai/whisper-large-v3-turbo",
                Duration.ofSeconds(30));

        assertThatThrownBy(() -> provider.transcribe(SpeechToTextRequest.builder()
                .audioBytes(new byte[] {1})
                .mimeType("audio/mpeg")
                .audioFormat("mp3")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("openrouter.api-key");
    }
}
