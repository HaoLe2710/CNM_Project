package fit.iuh.cnm_project_be.webrtc.dto;

public class SessionDescriptionPayload {

    private String type;
    private String sdp;

    public SessionDescriptionPayload() {
    }

    public SessionDescriptionPayload(String type, String sdp) {
        this.type = type;
        this.sdp = sdp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSdp() {
        return sdp;
    }

    public void setSdp(String sdp) {
        this.sdp = sdp;
    }
}
