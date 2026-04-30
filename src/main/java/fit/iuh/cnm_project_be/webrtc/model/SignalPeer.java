package fit.iuh.cnm_project_be.webrtc.model;

import fit.iuh.cnm_project_be.webrtc.dto.PeerInfo;
import org.springframework.web.socket.WebSocketSession;

public class SignalPeer {

    private final String userId;
    private final String displayName;
    private final WebSocketSession session;

    public SignalPeer(String userId, String displayName, WebSocketSession session) {
        this.userId = userId;
        this.displayName = displayName;
        this.session = session;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public PeerInfo toPeerInfo() {
        return new PeerInfo(userId, displayName);
    }
}
