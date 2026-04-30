package fit.iuh.cnm_project_be.webrtc.config;

import fit.iuh.cnm_project_be.webrtc.socket.WebRtcSignalingHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebRtcSignalWebSocketConfig implements WebSocketConfigurer {

    private final WebRtcSignalingHandler webRtcSignalingHandler;
    private final WebRtcSignalProperties webRtcSignalProperties;

    public WebRtcSignalWebSocketConfig(
            WebRtcSignalingHandler webRtcSignalingHandler,
            WebRtcSignalProperties webRtcSignalProperties) {
        this.webRtcSignalingHandler = webRtcSignalingHandler;
        this.webRtcSignalProperties = webRtcSignalProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webRtcSignalingHandler, webRtcSignalProperties.getSignalEndpoint())
                .setAllowedOriginPatterns(
                        webRtcSignalProperties.getAllowedOriginPatterns().toArray(String[]::new)
                );
    }
}
