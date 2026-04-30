package fit.iuh.cnm_project_be.webrtc.dto;

public enum SignalMessageType {
    JOIN_ROOM,
    LEAVE_ROOM,
    ROOM_JOINED,
    PEER_JOINED,
    PEER_LEFT,
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    ERROR
}
