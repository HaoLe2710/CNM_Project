package fit.iuh.cnm_project_be.webrtc.dto;

public class IceCandidatePayload {

    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
    private String usernameFragment;

    public IceCandidatePayload() {
    }

    public String getCandidate() {
        return candidate;
    }

    public void setCandidate(String candidate) {
        this.candidate = candidate;
    }

    public String getSdpMid() {
        return sdpMid;
    }

    public void setSdpMid(String sdpMid) {
        this.sdpMid = sdpMid;
    }

    public Integer getSdpMLineIndex() {
        return sdpMLineIndex;
    }

    public void setSdpMLineIndex(Integer sdpMLineIndex) {
        this.sdpMLineIndex = sdpMLineIndex;
    }

    public String getUsernameFragment() {
        return usernameFragment;
    }

    public void setUsernameFragment(String usernameFragment) {
        this.usernameFragment = usernameFragment;
    }
}
