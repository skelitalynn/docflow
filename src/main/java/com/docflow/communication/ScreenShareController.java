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
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        Long userId = SecurityUtils.getCurrentUserId();
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        ScreenShareService.ScreenShareState state = screenShareService.start(docId, userId, request.shareUrl());

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
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        Long userId = SecurityUtils.getCurrentUserId();
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        ScreenShareService.ScreenShareState state = screenShareService.stop(docId);

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
