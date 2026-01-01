package com.docflow.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Component
public class WebSocketNotifier {
    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry sessionRegistry;
    private final PresenceService presenceService;

    public WebSocketNotifier(ObjectMapper objectMapper,
                             WebSocketSessionRegistry sessionRegistry,
                             PresenceService presenceService) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.presenceService = presenceService;
    }

    public void sendToUser(Long userId, String type, Map<String, Object> data) {
        WsMessage message = new WsMessage(type, data);
        Set<String> sessionIds = sessionRegistry.getSessionsForUser(userId);
        for (String sessionId : sessionIds) {
            WebSocketSession session = sessionRegistry.getSession(sessionId);
            send(session, message);
        }
    }

    public void broadcastToDoc(Long docId, String type, Map<String, Object> data) {
        WsMessage message = new WsMessage(type, data);
        Set<String> sessionIds = presenceService.getSessionsForDoc(docId);
        for (String sessionId : sessionIds) {
            WebSocketSession session = sessionRegistry.getSession(sessionId);
            send(session, message);
        }
    }

    private void send(WebSocketSession session, WsMessage message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(payload));
        } catch (IOException ignored) {
        }
    }
}
