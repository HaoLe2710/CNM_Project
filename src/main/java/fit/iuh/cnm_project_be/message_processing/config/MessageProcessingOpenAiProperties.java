package fit.iuh.cnm_project_be.message_processing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.message-processing.openai")
public class MessageProcessingOpenAiProperties {

    private String apiKey;
    private String baseUrl = "https://api.openai.com";
    private String sttModel = "gpt-4o-mini-transcribe";
    private String ttsModel = "gpt-4o-mini-tts";
    private String defaultVoice = "alloy";
    private int timeoutSeconds = 45;
}
