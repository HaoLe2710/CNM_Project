package fit.iuh.cnm_project_be.webrtc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.webrtc.config.WebRtcSignalProperties;
import fit.iuh.cnm_project_be.webrtc.dto.PeerInfo;
import fit.iuh.cnm_project_be.webrtc.dto.SignalMessage;
import fit.iuh.cnm_project_be.webrtc.dto.SignalMessageType;
import fit.iuh.cnm_project_be.webrtc.model.SignalPeer;
import fit.iuh.cnm_project_be.webrtc.model.SignalRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class WebRtcSignalingService {

    private final ObjectMapper objectMapper;
    private final WebRtcSignalProperties webRtcSignalProperties;

    /**
     * roomId -> room
     */
    private final ConcurrentMap<String, SignalRoom> rooms = new ConcurrentHashMap<>();

    /**
     * sessionId -> (roomId, userId)
     */
    private final ConcurrentMap<String, SessionBinding> sessionBindings = new ConcurrentHashMap<>();

    public WebRtcSignalingService(ObjectMapper objectMapper, WebRtcSignalProperties webRtcSignalProperties) {
        this.objectMapper = objectMapper;
        this.webRtcSignalProperties = webRtcSignalProperties;
    }

    public void joinRoom(WebSocketSession session, SignalMessage message) throws IOException {
        if (!hasText(message.getRoomId()) || !hasText(message.getFromUserId())) {
            sendError(session, "JOIN_ROOM requires roomId and fromUserId");
            return;
        }

        leaveRoom(session, "Peer moved to another room", false);

        String roomId = message.getRoomId().trim();
        String userId = message.getFromUserId().trim();
        String displayName = hasText(message.getFromDisplayName()) ? message.getFromDisplayName().trim() : userId;

        SignalRoom room = rooms.computeIfAbsent(roomId, SignalRoom::new);
        List<PeerInfo> existingPeers;

        synchronized (room) {
            SignalPeer previousPeer = room.getPeer(userId);
            if (previousPeer != null && !previousPeer.getSession().getId().equals(session.getId())) {
                room.removePeer(userId);
                sessionBindings.remove(previousPeer.getSession().getId());
                closeQuietly(previousPeer.getSession(), CloseStatus.NORMAL);
            }

            int currentPeerCount = room.getPeer(userId) == null ? room.size() : room.size() - 1;
            if (currentPeerCount >= webRtcSignalProperties.getMeshMaxPeers()) {
                sendError(session, "Room is full for mesh mode. Use SFU for larger rooms.");
                return;
            }

            existingPeers = room.listPeerInfosExcluding(userId);
            room.addPeer(new SignalPeer(userId, displayName, session));
            sessionBindings.put(session.getId(), new SessionBinding(roomId, userId));
        }

        SignalMessage joinedMessage = new SignalMessage();
        joinedMessage.setType(SignalMessageType.ROOM_JOINED);
        joinedMessage.setRoomId(roomId);
        joinedMessage.setFromUserId(userId);
        joinedMessage.setFromDisplayName(displayName);
        joinedMessage.setPeers(existingPeers);
        joinedMessage.setMessage("Joined room successfully");
        send(session, joinedMessage);

        SignalMessage peerJoinedMessage = new SignalMessage();
        peerJoinedMessage.setType(SignalMessageType.PEER_JOINED);
        peerJoinedMessage.setRoomId(roomId);
        peerJoinedMessage.setFromUserId(userId);
        peerJoinedMessage.setFromDisplayName(displayName);
        peerJoinedMessage.setMessage("A new peer joined the room");
        broadcastToRoom(room, userId, peerJoinedMessage);

        log.info("[WebRtcSignalingService] userId={} joined roomId={} currentPeers={}",
                userId, roomId, room.size());
    }

    public void leaveRoom(WebSocketSession session) {
        leaveRoom(session, "Peer left the room", true);
    }

    public void relaySignal(WebSocketSession session, SignalMessage message) throws IOException {
        SessionBinding sessionBinding = sessionBindings.get(session.getId());
        if (sessionBinding == null) {
            sendError(session, "You must join a room before sending signaling data");
            return;
        }

        if (!hasText(message.getToUserId())) {
            sendError(session, "Signal message requires toUserId");
            return;
        }

        SignalRoom room = rooms.get(sessionBinding.roomId());
        if (room == null) {
            sendError(session, "Room does not exist anymore");
            return;
        }

        SignalPeer sourcePeer = room.getPeer(sessionBinding.userId());
        SignalPeer targetPeer = room.getPeer(message.getToUserId());
        if (sourcePeer == null || targetPeer == null) {
            sendError(session, "Target peer is not in the room");
            return;
        }

        SignalMessage forwardedMessage = new SignalMessage();
        forwardedMessage.setType(message.getType());
        forwardedMessage.setRoomId(sessionBinding.roomId());
        forwardedMessage.setFromUserId(sourcePeer.getUserId());
        forwardedMessage.setFromDisplayName(sourcePeer.getDisplayName());
        forwardedMessage.setToUserId(targetPeer.getUserId());
        forwardedMessage.setSdp(message.getSdp());
        forwardedMessage.setCandidate(message.getCandidate());
        forwardedMessage.setMessage(message.getMessage());

        send(targetPeer.getSession(), forwardedMessage);
    }

    public void handleDisconnect(WebSocketSession session) {
        leaveRoom(session, "Peer disconnected", true);
    }

    public void sendError(WebSocketSession session, String errorMessage) throws IOException {
        SignalMessage error = new SignalMessage();
        error.setType(SignalMessageType.ERROR);
        error.setMessage(errorMessage);
        send(session, error);
    }

    private void leaveRoom(WebSocketSession session, String reason, boolean logAction) {
        SessionBinding binding = sessionBindings.remove(session.getId());
        if (binding == null) {
            return;
        }

        SignalRoom room = rooms.get(binding.roomId());
        if (room == null) {
            return;
        }

        SignalPeer removedPeer;
        synchronized (room) {
            removedPeer = room.removePeer(binding.userId());
            if (room.getPeers().isEmpty()) {
                rooms.remove(binding.roomId(), room);
            }
        }

        if (removedPeer == null) {
            return;
        }

        SignalMessage peerLeftMessage = new SignalMessage();
        peerLeftMessage.setType(SignalMessageType.PEER_LEFT);
        peerLeftMessage.setRoomId(binding.roomId());
        peerLeftMessage.setFromUserId(removedPeer.getUserId());
        peerLeftMessage.setFromDisplayName(removedPeer.getDisplayName());
        peerLeftMessage.setMessage(reason);

        broadcastToRoom(room, removedPeer.getUserId(), peerLeftMessage);

        if (logAction) {
            log.info("[WebRtcSignalingService] userId={} left roomId={}",
                    removedPeer.getUserId(), binding.roomId());
        }
    }

    private void broadcastToRoom(SignalRoom room, String excludedUserId, SignalMessage message) {
        room.getPeers().values().stream()
                .filter(peer -> !peer.getUserId().equals(excludedUserId))
                .forEach(peer -> {
                    try {
                        send(peer.getSession(), message);
                    } catch (IOException error) {
                        log.warn("[WebRtcSignalingService] Failed to broadcast to userId={} roomId={}: {}",
                                peer.getUserId(), room.getRoomId(), error.getMessage());
                    }
                });
    }

    private void send(WebSocketSession session, SignalMessage message) throws IOException {
        if (!session.isOpen()) {
            return;
        }

        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus closeStatus) {
        try {
            if (session.isOpen()) {
                session.close(closeStatus);
            }
        } catch (IOException error) {
            log.debug("[WebRtcSignalingService] Failed to close session {}: {}",
                    session.getId(), error.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SessionBinding(String roomId, String userId) {
    }
}
