package fit.iuh.cnm_project_be.webrtc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.webrtc")
public class WebRtcSignalProperties {

    /**
     * Raw WebSocket endpoint used by the React/React Native signaling client.
     */
    private String signalEndpoint = "/signal";

    /**
     * Public websocket URL returned to clients through call APIs and push payloads.
     */
    private String signalPublicUrl = "ws://127.0.0.1:8080/signal";

    /**
     * Mesh rooms are intentionally kept small because every peer sends media
     * directly to every other peer.
     */
    private int meshMaxPeers = 4;

    /**
     * Allowed origins for the WebSocket handshake.
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.*:*",
            "http://10.*:*"
    ));

    public String getSignalEndpoint() {
        return signalEndpoint;
    }

    public void setSignalEndpoint(String signalEndpoint) {
        this.signalEndpoint = signalEndpoint;
    }

    public String getSignalPublicUrl() {
        return signalPublicUrl;
    }

    public void setSignalPublicUrl(String signalPublicUrl) {
        this.signalPublicUrl = signalPublicUrl;
    }

    public int getMeshMaxPeers() {
        return meshMaxPeers;
    }

    public void setMeshMaxPeers(int meshMaxPeers) {
        this.meshMaxPeers = meshMaxPeers;
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }
}
