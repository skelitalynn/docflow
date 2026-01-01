package com.docflow.collaboration;

import com.docflow.acl.AclService;
import com.docflow.common.DocRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CollaborationWebSocketHandler extends TextWebSocketHandler {
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final PresenceService presenceService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketNotifier notifier;
    private final AclService aclService;

    public CollaborationWebSocketHandler(ObjectMapper objectMapper,
                                         PresenceService presenceService,
                                         WebSocketSessionRegistry sessionRegistry,
                                         WebSocketNotifier notifier,
                                         AclService aclService) {
        this.objectMapper = objectMapper;
        this.presenceService = presenceService;
        this.sessionRegistry = sessionRegistry;
        this.notifier = notifier;
        this.aclService = aclService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionRegistry.register(userId, session);
            presenceService.heartbeat(session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsMessage incoming = objectMapper.readValue(message.getPayload(), WsMessage.class);
        if (incoming.getType() == null) {
            return;
        }
        switch (incoming.getType()) {
            case "presence.join" -> handleJoin(session, incoming.getData());
            case "presence.leave" -> handleLeave(session);
            case "presence.ping" -> presenceService.heartbeat(session.getId());
            default -> {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long docId = presenceService.leave(session.getId());
        sessionRegistry.remove(session.getId());
        if (docId != null) {
            broadcastPresence(docId, "presence.leave");
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void cleanupStalePresence() {
        Map<Long, List<String>> removed = presenceService.removeStaleSessions(PRESENCE_TTL);
        for (Long docId : removed.keySet()) {
            broadcastPresence(docId, "presence.leave");
        }
    }

    private void handleJoin(WebSocketSession session, Map<String, Object> data) {
        Long userId = (Long) session.getAttributes().get("userId");
        String nickname = (String) session.getAttributes().get("nickname");
        Long docId = asLong(data, "docId");
        if (userId == null || docId == null) {
            return;
        }
        Long previousDoc = presenceService.leave(session.getId());
        if (previousDoc != null && !previousDoc.equals(docId)) {
            broadcastPresence(previousDoc, "presence.leave");
        }
        DocRole role = aclService.requireRole(userId, docId, DocRole.VIEWER);
        if (role == null) {
            return;
        }
        presenceService.join(docId, session.getId(), userId, nickname);
        broadcastPresence(docId, "presence.join");
    }

    private void handleLeave(WebSocketSession session) {
        Long docId = presenceService.leave(session.getId());
        if (docId != null) {
            broadcastPresence(docId, "presence.leave");
        }
    }

    private void broadcastPresence(Long docId, String type) {
        List<PresenceService.PresenceMember> members = presenceService.getMembers(docId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", docId);
        payload.put("members", members);
        notifier.broadcastToDoc(docId, type, payload);
    }

    private Long asLong(Map<String, Object> data, String key) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
