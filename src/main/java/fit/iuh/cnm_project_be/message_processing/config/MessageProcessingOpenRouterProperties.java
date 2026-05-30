package fit.iuh.cnm_project_be.message_processing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.message-processing.openrouter")
public class MessageProcessingOpenRouterProperties {

    private String apiKey;
    private String baseUrl = "https://openrouter.ai/api/v1";
    private String sttModel = "openai/whisper-large-v3-turbo";
    private int timeoutSeconds = 60;
}
