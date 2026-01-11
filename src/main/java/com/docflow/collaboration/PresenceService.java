package com.docflow.collaboration;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PresenceService {
    // docId -> (sessionId -> member)，用于广播在线成员列表。
    private final Map<Long, Map<String, PresenceMember>> docSessions = new ConcurrentHashMap<>();
    // sessionId -> docId，便于断开时快速清理。
    private final Map<String, Long> sessionDoc = new ConcurrentHashMap<>();
    // sessionId -> 最近心跳时间。
    private final Map<String, Instant> heartbeat = new ConcurrentHashMap<>();

    public void join(Long docId, String sessionId, Long userId, String nickname) {
        docSessions.computeIfAbsent(docId, id -> new ConcurrentHashMap<>())
                .put(sessionId, new PresenceMember(userId, nickname));
        sessionDoc.put(sessionId, docId);
        heartbeat.put(sessionId, Instant.now());
    }

    public Long leave(String sessionId) {
        Long docId = sessionDoc.remove(sessionId);
        heartbeat.remove(sessionId);
        if (docId == null) {
            return null;
        }
        Map<String, PresenceMember> sessions = docSessions.get(docId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                docSessions.remove(docId);
            }
        }
        return docId;
    }

    public void heartbeat(String sessionId) {
        heartbeat.put(sessionId, Instant.now());
    }

    public List<PresenceMember> getMembers(Long docId) {
        Map<String, PresenceMember> sessions = docSessions.get(docId);
        if (sessions == null) {
            return Collections.emptyList();
        }
        // 同一用户可能多端连接，这里按 userId 去重。
        return sessions.values().stream()
                .collect(Collectors.toMap(PresenceMember::userId, member -> member, (a, b) -> a))
                .values()
                .stream()
                .toList();
    }

    public Set<String> getSessionsForDoc(Long docId) {
        Map<String, PresenceMember> sessions = docSessions.get(docId);
        if (sessions == null) {
            return Collections.emptySet();
        }
        return sessions.keySet();
    }

    public Map<Long, List<String>> removeStaleSessions(Duration ttl) {
        Instant cutoff = Instant.now().minus(ttl);
        Map<Long, List<String>> removedByDoc = new HashMap<>();
        for (Map.Entry<String, Instant> entry : new ArrayList<>(heartbeat.entrySet())) {
            // 超过 TTL 未心跳的会话视为离线。
            if (entry.getValue().isBefore(cutoff)) {
                String sessionId = entry.getKey();
                Long docId = leave(sessionId);
                if (docId != null) {
                    removedByDoc.computeIfAbsent(docId, id -> new ArrayList<>()).add(sessionId);
                }
            }
        }
        return removedByDoc;
    }

    public record PresenceMember(Long userId, String nickname) {
    }
}
