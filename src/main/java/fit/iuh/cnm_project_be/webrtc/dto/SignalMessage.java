package fit.iuh.cnm_project_be.webrtc.dto;

import java.util.ArrayList;
import java.util.List;

public class SignalMessage {

    private SignalMessageType type;
    private String roomId;
    private String fromUserId;
    private String fromDisplayName;
    private String toUserId;
    private List<PeerInfo> peers = new ArrayList<>();
    private SessionDescriptionPayload sdp;
    private IceCandidatePayload candidate;
    private String message;

    public SignalMessageType getType() {
        return type;
    }

    public void setType(SignalMessageType type) {
        this.type = type;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getFromDisplayName() {
        return fromDisplayName;
    }

    public void setFromDisplayName(String fromDisplayName) {
        this.fromDisplayName = fromDisplayName;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public List<PeerInfo> getPeers() {
        return peers;
    }

    public void setPeers(List<PeerInfo> peers) {
        this.peers = peers;
    }

    public SessionDescriptionPayload getSdp() {
        return sdp;
    }

    public void setSdp(SessionDescriptionPayload sdp) {
        this.sdp = sdp;
    }

    public IceCandidatePayload getCandidate() {
        return candidate;
    }

    public void setCandidate(IceCandidatePayload candidate) {
        this.candidate = candidate;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
