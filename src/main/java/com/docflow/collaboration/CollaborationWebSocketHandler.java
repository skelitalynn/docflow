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

// 协作 WebSocket 处理器，接收前端发来的协作消息
@Component
public class CollaborationWebSocketHandler extends TextWebSocketHandler {
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final PresenceService presenceService;
    private final CursorService cursorService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketNotifier notifier;
    private final AclService aclService;

    public CollaborationWebSocketHandler(ObjectMapper objectMapper,
                                         PresenceService presenceService,
                                         CursorService cursorService,
                                         WebSocketSessionRegistry sessionRegistry,
                                         WebSocketNotifier notifier,
                                         AclService aclService) {
        this.objectMapper = objectMapper;
        this.presenceService = presenceService;
        this.cursorService = cursorService;
        this.sessionRegistry = sessionRegistry;
        this.notifier = notifier;
        this.aclService = aclService;
    }

    // 在 WebSocket 连接建立时注册用户和会话，并发送心跳
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionRegistry.register(userId, session);
            presenceService.heartbeat(session.getId());
        }
    }

    //接收消息总入口
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsMessage incoming = objectMapper.readValue(message.getPayload(), WsMessage.class);
        if (incoming.getType() == null) {
            return;
        }
        //用switch做分发，将JSON变成Java对象
        switch (incoming.getType()) {
            case "presence.join" -> handleJoin(session, incoming.getData());
            case "presence.leave" -> handleLeave(session);
            case "presence.ping" -> presenceService.heartbeat(session.getId());
            case "cursor.update" -> handleCursorUpdate(session, incoming.getData());
            case "doc.edit" -> handleDocEdit(session, incoming.getData());
            default -> {
            }
        }
    }
    
    //断开连接
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long docId = presenceService.leave(session.getId());
        //从列表删除用户
        sessionRegistry.remove(session.getId());
        if (docId != null) {
            Long userId = (Long) session.getAttributes().get("userId");
            if (userId != null) {
                //删除光标
                cursorService.remove(docId, userId);
                broadcastCursors(docId);
            }
            //通知所有人
            broadcastPresence(docId, "presence.leave");
        }
    }

    @Scheduled(fixedDelay = 10)
    //定时清理
    public void cleanupStalePresence() {
        Map<Long, List<String>> removed = presenceService.removeStaleSessions(PRESENCE_TTL);
        for (Long docId : removed.keySet()) {
            broadcastPresence(docId, "presence.leave");
        }
        Map<Long, List<Long>> cursorRemoved = cursorService.removeStale(PRESENCE_TTL);
        for (Long docId : cursorRemoved.keySet()) {
            broadcastCursors(docId);
        }
    }

    //加入文档协作
    private void handleJoin(WebSocketSession session, Map<String, Object> data) {
        Long userId = (Long) session.getAttributes().get("userId");
        String nickname = (String) session.getAttributes().get("nickname");
        Long docId = asLong(data, "docId");
        if (userId == null || docId == null) {
            return;
        }
        //如果user之前在别的文档，离开
        //一个人同时只能协作一篇文档
        Long previousDoc = presenceService.leave(session.getId());
        if (previousDoc != null && !previousDoc.equals(docId)) {
            broadcastPresence(previousDoc, "presence.leave");
        }
        //权限检查，至少为VIEWER
        DocRole role = aclService.requireRole(userId, docId, DocRole.VIEWER);
        if (role == null) {
            return;
        }
        presenceService.join(docId, session.getId(), userId, nickname);
        broadcastPresence(docId, "presence.join");
        broadcastCursors(docId);
    }

    private void handleLeave(WebSocketSession session) {
        Long docId = presenceService.leave(session.getId());
        if (docId != null) {
            Long userId = (Long) session.getAttributes().get("userId");
            if (userId != null) {
                cursorService.remove(docId, userId);
                broadcastCursors(docId);
            }
            broadcastPresence(docId, "presence.leave");
        }
    }

    //用户在编辑器移动光标
    private void handleCursorUpdate(WebSocketSession session, Map<String, Object> data) {
        Long userId = (Long) session.getAttributes().get("userId");
        String nickname = (String) session.getAttributes().get("nickname");
        Long docId = asLong(data, "docId");
        Integer line = asInt(data, "line");
        Integer column = asInt(data, "column");
        if (userId == null || docId == null || line == null || column == null) {
            return;
        }
        //查看是不是EDITOR
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        //将最新光标发给所有人
        cursorService.update(docId, userId, nickname, line, column);
        broadcastCursors(docId);
    }

    //编辑内容
    private void handleDocEdit(WebSocketSession session, Map<String, Object> data) {
        Long userId = (Long) session.getAttributes().get("userId");
        Long docId = asLong(data, "docId");
        if (userId == null || docId == null) {
            return;
        }
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", docId);
        payload.put("actorId", userId);
        payload.put("content", data != null ? data.get("content") : null);
        payload.put("format", data != null ? data.get("format") : null);
        payload.put("baseVersion", data != null ? data.get("baseVersion") : null);
        //将某个人在编辑的内容广播给别人
        notifier.broadcastToDoc(docId, "doc.edit", payload);
    }

    private void broadcastPresence(Long docId, String type) {
        List<PresenceService.PresenceMember> members = presenceService.getMembers(docId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", docId);
        payload.put("members", members);
        notifier.broadcastToDoc(docId, type, payload);
    }

    private void broadcastCursors(Long docId) {
        List<CursorService.CursorState> cursors = cursorService.getCursors(docId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", docId);
        payload.put("cursors", cursors);
        notifier.broadcastToDoc(docId, "cursor.update", payload);
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

    private Integer asInt(Map<String, Object> data, String key) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
