package com.docflow.fileshare;

import com.docflow.acl.AclService;
import com.docflow.audit.AuditService;
import com.docflow.collaboration.WebSocketNotifier;
import com.docflow.common.DocRole;
import com.docflow.common.NotFoundException;
import com.docflow.document.Document;
import com.docflow.document.DocumentRepository;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

// 文件共享业务：存储文件、记录元数据、广播协作者、写入审计。
@Service
public class SharedFileService {
    private final SharedFileRepository fileRepository;
    private final SharedFileStorageService storageService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AclService aclService;
    private final WebSocketNotifier notifier;
    private final AuditService auditService;

    public SharedFileService(SharedFileRepository fileRepository,
                             SharedFileStorageService storageService,
                             DocumentRepository documentRepository,
                             UserRepository userRepository,
                             AclService aclService,
                             WebSocketNotifier notifier,
                             AuditService auditService) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aclService = aclService;
        this.notifier = notifier;
        this.auditService = auditService;
    }

    @Transactional
    public SharedFile upload(Long userId, Long docId, MultipartFile file, String ip) {
        // 1) 校验文档存在与编辑权限。
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        // 2) 写入磁盘并返回存储信息。
        SharedFileStorageService.StoredFile stored = storageService.store(file);
        // 3) 落库文件元数据。
        SharedFile entity = SharedFile.builder()
                .document(document)
                .uploader(uploader)
                .originalName(stored.originalName())
                .storageName(stored.storageName())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .downloadUrl("")
                .build();
        SharedFile saved = fileRepository.save(entity);
        saved.setDownloadUrl("/files/" + saved.getId());
        SharedFile updated = fileRepository.save(saved);

        // 4) WS 广播给文档协作者，前端可实时刷新文件列表。
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", updated.getId());
        payload.put("docId", docId);
        payload.put("uploaderId", uploader.getId());
        payload.put("name", updated.getOriginalName());
        payload.put("downloadUrl", updated.getDownloadUrl());
        payload.put("sizeBytes", updated.getSizeBytes());
        notifier.broadcastToDoc(docId, "file.shared", payload);

        // 5) 写入审计日志，记录上传行为。
        auditService.record(uploader, "file_upload", "file", updated.getId(), document, true, null, ip, null);
        return updated;
    }

    public Page<SharedFile> list(Long userId, Long docId, Pageable pageable) {
        // 列表：只需要查看权限，按创建时间倒序。
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        return fileRepository.findByDocumentIdOrderByCreatedAtDesc(docId, pageable);
    }

    public SharedFile get(Long userId, Long fileId) {
        // 下载前做权限校验，防止越权访问。
        SharedFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new NotFoundException("File not found"));
        aclService.requireRole(userId, file.getDocument().getId(), DocRole.VIEWER);
        return file;
    }
}
