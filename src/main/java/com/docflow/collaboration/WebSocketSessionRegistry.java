package com.docflow.collaboration;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.put(session.getId(), session);
        userSessions.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(session.getId());
    }

    public void remove(String sessionId) {
        WebSocketSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long id) {
            Set<String> ids = userSessions.get(id);
            if (ids != null) {
                ids.remove(sessionId);
                if (ids.isEmpty()) {
                    userSessions.remove(id);
                }
            }
        }
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public Set<String> getSessionsForUser(Long userId) {
        return userSessions.getOrDefault(userId, Collections.emptySet());
    }
}
