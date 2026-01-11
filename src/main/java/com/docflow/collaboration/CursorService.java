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
    //两层Map，第一层docId一个文档一组光标，第二层userId一个用户一个光标（唯一）
    //每个用户在每个文档中最多一个一个光标
    private final Map<Long, Map<Long, CursorState>> cursors = new ConcurrentHashMap<>();

    //更新光标
    public void update(Long docId, Long userId, String nickname, int line, int column) {
        cursors.computeIfAbsent(docId, id -> new ConcurrentHashMap<>())
                .put(userId, new CursorState(userId, nickname, line, column, Instant.now()));
    }

    //移除user
    public void remove(Long docId, Long userId) {
        Map<Long, CursorState> docCursors = cursors.get(docId);
        if (docCursors == null) {
            return;
        }
        docCursors.remove(userId);

        //如果文档已经没人，那也不用记录光标了
        if (docCursors.isEmpty()) {
            cursors.remove(docId);
        }
    }

    //将所有人的光标位置拿出来，抄一份给前端
    public List<CursorState> getCursors(Long docId) {
        Map<Long, CursorState> docCursors = cursors.get(docId);
        if (docCursors == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(docCursors.values());
    }

    //如果用户很久没有移动光标，将其删除
    public Map<Long, List<Long>> removeStale(Duration ttl) {
        Instant cutoff = Instant.now().minus(ttl);
        Map<Long, List<Long>> removed = new HashMap<>();
        for (Map.Entry<Long, Map<Long, CursorState>> entry : new HashMap<>(cursors).entrySet()) {
            Long docId = entry.getKey();
            Map<Long, CursorState> docCursors = entry.getValue();
            for (Map.Entry<Long, CursorState> cursorEntry : new HashMap<>(docCursors).entrySet()) {
                //如果在cutoff还未移动光标，进行删除
                if (cursorEntry.getValue().updatedAt().isBefore(cutoff)) {
                    Long userId = cursorEntry.getKey();
                    docCursors.remove(userId);
                    //日志记录
                    removed.computeIfAbsent(docId, id -> new ArrayList<>()).add(userId);
                }
            }
            //判断doc里是否还有user，如果没有也一起删掉
            if (docCursors.isEmpty()) {
                cursors.remove(docId);
            }
        }
        return removed;
    }

    public record CursorState(Long userId, String nickname, int line, int column, Instant updatedAt) {
    }
}
