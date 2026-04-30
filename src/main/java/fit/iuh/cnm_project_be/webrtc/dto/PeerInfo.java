package fit.iuh.cnm_project_be.webrtc.dto;

public class PeerInfo {

    private String userId;
    private String displayName;

    public PeerInfo() {
    }

    public PeerInfo(String userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
