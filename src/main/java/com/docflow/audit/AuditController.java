package com.docflow.audit;

import com.docflow.acl.AclService;
import com.docflow.common.NotFoundException;
import com.docflow.common.PageResponse;
import com.docflow.document.DocumentRepository;
import com.docflow.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/docs")
public class AuditController {
    private final AuditLogRepository auditLogRepository;
    private final DocumentRepository documentRepository;
    private final AclService aclService;

    public AuditController(AuditLogRepository auditLogRepository,
                           DocumentRepository documentRepository,
                           AclService aclService) {
        this.auditLogRepository = auditLogRepository;
        this.documentRepository = documentRepository;
        this.aclService = aclService;
    }

    @GetMapping("/{id}/audit")
    public PageResponse<AuditResponse> list(@PathVariable("id") Long docId,
                                            @RequestParam(value = "page", defaultValue = "0") int page,
                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.ensureOwnerOrAdmin(SecurityUtils.getCurrentUserId(), docId);
        Page<AuditLog> logs = auditLogRepository.findByDocumentIdOrderByCreatedAtDesc(docId, PageRequest.of(page, size));
        List<AuditResponse> items = logs.map(this::toResponse).getContent();
        return new PageResponse<>(items, logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages());
    }

    private AuditResponse toResponse(AuditLog log) {
        return new AuditResponse(
                log.getId(),
                log.getActor().getId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.isResult(),
                log.getErrorMsg(),
                log.getIp(),
                log.getMeta(),
                log.getCreatedAt()
        );
    }
}
