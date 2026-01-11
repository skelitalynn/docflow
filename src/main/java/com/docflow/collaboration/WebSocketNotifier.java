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
    private final ObjectMapper objectMapper;//将Java对象变成JSON
    private final WebSocketSessionRegistry sessionRegistry;//知道sessionId对应哪条WebSocket
    private final PresenceService presenceService;//知道哪个文档有什么人在线

    public WebSocketNotifier(ObjectMapper objectMapper,
                             WebSocketSessionRegistry sessionRegistry,
                             PresenceService presenceService) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.presenceService = presenceService;
    }
    
    //给某一个user发送消息
    public void sendToUser(Long userId, String type, Map<String, Object> data) {
        // 单播到用户的所有在线连接。
        WsMessage message = new WsMessage(type, data);
        Set<String> sessionIds = sessionRegistry.getSessionsForUser(userId);
        //查这个人在线的session，看他连着几条WebSocket
        for (String sessionId : sessionIds) {
            WebSocketSession session = sessionRegistry.getSession(sessionId);
            send(session, message);
        }
    }

    //发给整个文档的人
    public void broadcastToDoc(Long docId, String type, Map<String, Object> data) {
        // 广播到文档当前在线成员。
        WsMessage message = new WsMessage(type, data);
        Set<String> sessionIds = presenceService.getSessionsForDoc(docId);
        for (String sessionId : sessionIds) {
            WebSocketSession session = sessionRegistry.getSession(sessionId);
            send(session, message);
        }
    }


    //真正发消息的函数
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
