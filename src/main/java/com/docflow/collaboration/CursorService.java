package com.docflow.collaboration;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CursorService {
    private final Map<Long, Map<Long, CursorState>> cursors = new ConcurrentHashMap<>();

    public void update(Long docId, Long userId, String nickname, int line, int column) {
        cursors.computeIfAbsent(docId, id -> new ConcurrentHashMap<>())
                .put(userId, new CursorState(userId, nickname, line, column, Instant.now()));
    }

    public void remove(Long docId, Long userId) {
        Map<Long, CursorState> docCursors = cursors.get(docId);
        if (docCursors == null) {
            return;
        }
        docCursors.remove(userId);
        if (docCursors.isEmpty()) {
            cursors.remove(docId);
        }
    }

    public List<CursorState> getCursors(Long docId) {
        Map<Long, CursorState> docCursors = cursors.get(docId);
        if (docCursors == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(docCursors.values());
    }

    public Map<Long, List<Long>> removeStale(Duration ttl) {
        Instant cutoff = Instant.now().minus(ttl);
        Map<Long, List<Long>> removed = new HashMap<>();
        for (Map.Entry<Long, Map<Long, CursorState>> entry : new HashMap<>(cursors).entrySet()) {
            Long docId = entry.getKey();
            Map<Long, CursorState> docCursors = entry.getValue();
            for (Map.Entry<Long, CursorState> cursorEntry : new HashMap<>(docCursors).entrySet()) {
                if (cursorEntry.getValue().updatedAt().isBefore(cutoff)) {
                    Long userId = cursorEntry.getKey();
                    docCursors.remove(userId);
                    removed.computeIfAbsent(docId, id -> new ArrayList<>()).add(userId);
                }
            }
            if (docCursors.isEmpty()) {
                cursors.remove(docId);
            }
        }
        return removed;
    }

    public record CursorState(Long userId, String nickname, int line, int column, Instant updatedAt) {
    }
}
