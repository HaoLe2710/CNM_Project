package fit.iuh.cnm_project_be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final String DEFAULT_ALLOWED_ORIGINS = "http://localhost:5173,http://localhost:3000";

    @Value("${app.ws.allowed-origins:${APP_WS_ALLOWED_ORIGINS:${app.cors.allowed-origins:${APP_CORS_ALLOWED_ORIGINS:" + DEFAULT_ALLOWED_ORIGINS + "}}}}")
    private String wsAllowedOriginsRaw;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = parseAllowedOrigins(wsAllowedOriginsRaw);

        // Endpoint cũ cho chat
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                .setDisconnectDelay(30 * 1000)
                .setHeartbeatTime(10000);

        // Endpoint mới dành riêng cho các tác vụ Auth (như duyệt thiết bị mới)
        registry.addEndpoint("/auth/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                .setDisconnectDelay(30 * 1000)
                .setHeartbeatTime(10000);
    }

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-thread-");
        scheduler.initialize();
        return scheduler;
    }

    private String[] parseAllowedOrigins(String rawOrigins) {
        String source = rawOrigins;
        if (source == null || source.isBlank()) {
            source = DEFAULT_ALLOWED_ORIGINS;
        }

        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
