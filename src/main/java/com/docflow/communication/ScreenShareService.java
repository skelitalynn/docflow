package com.docflow.communication;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 屏幕共享的内存态管理：仅记录当前共享状态，不做持久化。
@Service
public class ScreenShareService {
    // docId -> 当前共享状态
    private final Map<Long, ScreenShareState> activeShares = new ConcurrentHashMap<>();

    public ScreenShareState start(Long docId, Long userId, String shareUrl) {
        // 开始共享时覆盖同文档的历史状态。
        ScreenShareState state = new ScreenShareState(docId, userId, shareUrl, LocalDateTime.now());
        activeShares.put(docId, state);
        return state;
    }

    public ScreenShareState stop(Long docId) {
        // 结束共享直接移除状态。
        return activeShares.remove(docId);
    }

    public ScreenShareState get(Long docId) {
        // 查询当前共享状态（为空表示未共享）。
        return activeShares.get(docId);
    }

    public record ScreenShareState(Long docId, Long userId, String shareUrl, LocalDateTime startedAt) {
    }
}
