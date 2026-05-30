package fit.iuh.cnm_project_be.presence.websocket;

import fit.iuh.cnm_project_be.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceWebSocketEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        UUID userId = extractUserId(accessor);
        if (userId == null) {
            return;
        }

        presenceService.markConnected(userId, sessionId);
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        presenceService.markDisconnected(event.getSessionId());
    }

    private UUID extractUserId(StompHeaderAccessor accessor) {
        String userIdFromHeader = accessor.getFirstNativeHeader("x-user-id");
        UUID resolvedFromHeader = parseUuid(userIdFromHeader);
        if (resolvedFromHeader != null) {
            return resolvedFromHeader;
        }

        Principal principal = accessor.getUser();
        if (principal != null) {
            UUID resolvedFromPrincipal = parseUuid(principal.getName());
            if (resolvedFromPrincipal != null) {
                return resolvedFromPrincipal;
            }
        }

        return null;
    }

    private UUID parseUuid(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException ex) {
            log.debug("[Presence] Ignore websocket session with invalid user id: {}", rawValue);
            return null;
        }
    }
}

