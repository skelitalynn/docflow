package com.docflow.communication;

import com.docflow.acl.AclService;
import com.docflow.collaboration.WebSocketNotifier;
import com.docflow.common.DocRole;
import com.docflow.common.NotFoundException;
import com.docflow.document.DocumentRepository;
import com.docflow.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// 屏幕共享接口：开始/结束/查询，并通过 WS 广播给文档成员。
@RestController
@RequestMapping
public class ScreenShareController {
    private final ScreenShareService screenShareService;
    private final DocumentRepository documentRepository;
    private final AclService aclService;
    private final WebSocketNotifier notifier;

    public ScreenShareController(ScreenShareService screenShareService,
                                 DocumentRepository documentRepository,
                                 AclService aclService,
                                 WebSocketNotifier notifier) {
        this.screenShareService = screenShareService;
        this.documentRepository = documentRepository;
        this.aclService = aclService;
        this.notifier = notifier;
    }

    @PostMapping("/docs/{id}/screen-share/start")
    public ScreenShareResponse start(@PathVariable("id") Long docId,
                                     @Valid @RequestBody ScreenShareRequest request) {
        // 1) 校验文档存在与编辑权限。
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        Long userId = SecurityUtils.getCurrentUserId();
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        // 2) 写入共享状态。
        ScreenShareService.ScreenShareState state = screenShareService.start(docId, userId, request.shareUrl());

        // 3) 广播共享开始事件，协作者端刷新 UI。
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", docId);
        payload.put("userId", userId);
        payload.put("shareUrl", state.shareUrl());
        payload.put("startedAt", state.startedAt());
        notifier.broadcastToDoc(docId, "screen.share.start", payload);

        return new ScreenShareResponse(true, state.docId(), state.userId(), state.shareUrl(), state.startedAt());
    }

    @PostMapping("/docs/{id}/screen-share/stop")
    public ScreenShareResponse stop(@PathVariable("id") Long docId) {
        // 结束共享同样要求编辑权限。
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        Long userId = SecurityUtils.getCurrentUserId();
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        ScreenShareService.ScreenShareState state = screenShareService.stop(docId);

        // 广播共享结束事件。
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", docId);
        payload.put("userId", userId);
        notifier.broadcastToDoc(docId, "screen.share.stop", payload);

        if (state == null) {
            return new ScreenShareResponse(false, docId, null, null, null);
        }
        return new ScreenShareResponse(false, docId, state.userId(), state.shareUrl(), state.startedAt());
    }

    @GetMapping("/docs/{id}/screen-share")
    public ScreenShareResponse status(@PathVariable("id") Long docId) {
        // 查询共享状态只需查看权限。
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        Long userId = SecurityUtils.getCurrentUserId();
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        ScreenShareService.ScreenShareState state = screenShareService.get(docId);
        if (state == null) {
            return new ScreenShareResponse(false, docId, null, null, null);
        }
        return new ScreenShareResponse(true, docId, state.userId(), state.shareUrl(), state.startedAt());
    }

    public record ScreenShareRequest(@Size(max = 255) String shareUrl) {
    }

    public record ScreenShareResponse(boolean active,
                                      Long docId,
                                      Long userId,
                                      String shareUrl,
                                      LocalDateTime startedAt) {
    }
}
