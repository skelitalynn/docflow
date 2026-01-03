package com.docflow.communication;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScreenShareService {
    private final Map<Long, ScreenShareState> activeShares = new ConcurrentHashMap<>();

    public ScreenShareState start(Long docId, Long userId, String shareUrl) {
        ScreenShareState state = new ScreenShareState(docId, userId, shareUrl, LocalDateTime.now());
        activeShares.put(docId, state);
        return state;
    }

    public ScreenShareState stop(Long docId) {
        return activeShares.remove(docId);
    }

    public ScreenShareState get(Long docId) {
        return activeShares.get(docId);
    }

    public record ScreenShareState(Long docId, Long userId, String shareUrl, LocalDateTime startedAt) {
    }
}
