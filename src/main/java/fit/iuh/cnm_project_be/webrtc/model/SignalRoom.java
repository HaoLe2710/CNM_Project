package fit.iuh.cnm_project_be.webrtc.model;

import fit.iuh.cnm_project_be.webrtc.dto.PeerInfo;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SignalRoom {

    private final String roomId;
    private final ConcurrentMap<String, SignalPeer> peers = new ConcurrentHashMap<>();

    public SignalRoom(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    public ConcurrentMap<String, SignalPeer> getPeers() {
        return peers;
    }

    public int size() {
        return peers.size();
    }

    public SignalPeer getPeer(String userId) {
        return peers.get(userId);
    }

    public SignalPeer addPeer(SignalPeer peer) {
        return peers.put(peer.getUserId(), peer);
    }

    public SignalPeer removePeer(String userId) {
        return peers.remove(userId);
    }

    public List<PeerInfo> listPeerInfosExcluding(String excludedUserId) {
        return peers.values().stream()
                .filter(peer -> !peer.getUserId().equals(excludedUserId))
                .map(SignalPeer::toPeerInfo)
                .toList();
    }
}
