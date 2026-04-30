package fit.iuh.cnm_project_be.webrtc.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.iuh.cnm_project_be.webrtc.dto.SignalMessage;
import fit.iuh.cnm_project_be.webrtc.dto.SignalMessageType;
import fit.iuh.cnm_project_be.webrtc.service.WebRtcSignalingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class WebRtcSignalingHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final WebRtcSignalingService webRtcSignalingService;

    public WebRtcSignalingHandler(
            ObjectMapper objectMapper,
            WebRtcSignalingService webRtcSignalingService) {
        this.objectMapper = objectMapper;
        this.webRtcSignalingService = webRtcSignalingService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[WebRtcSignalingHandler] WebSocket connected: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        SignalMessage message;
        try {
            message = objectMapper.readValue(textMessage.getPayload(), SignalMessage.class);
        } catch (Exception error) {
            webRtcSignalingService.sendError(session, "Invalid JSON payload");
            return;
        }

        if (message.getType() == null) {
            webRtcSignalingService.sendError(session, "Message type is required");
            return;
        }

        switch (message.getType()) {
            case JOIN_ROOM -> webRtcSignalingService.joinRoom(session, message);
            case LEAVE_ROOM -> webRtcSignalingService.leaveRoom(session);
            case OFFER, ANSWER, ICE_CANDIDATE -> webRtcSignalingService.relaySignal(session, message);
            default -> webRtcSignalingService.sendError(
                    session,
                    "Unsupported client message type: " + message.getType()
            );
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        webRtcSignalingService.handleDisconnect(session);
        log.info("[WebRtcSignalingHandler] WebSocket closed: sessionId={} code={} reason={}",
                session.getId(), status.getCode(), status.getReason());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("[WebRtcSignalingHandler] Transport error on sessionId={}: {}",
                session.getId(), exception.getMessage());
        webRtcSignalingService.handleDisconnect(session);

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}
