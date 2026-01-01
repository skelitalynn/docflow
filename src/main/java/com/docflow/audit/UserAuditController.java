package com.docflow.audit;

import com.docflow.common.PageResponse;
import com.docflow.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserAuditController {
    private final AuditLogRepository auditLogRepository;

    public UserAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/me/audit")
    public PageResponse<AuditResponse> list(@RequestParam(value = "page", defaultValue = "0") int page,
                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<AuditLog> logs = auditLogRepository.findByActorIdOrderByCreatedAtDesc(
                SecurityUtils.getCurrentUserId(),
                PageRequest.of(page, size));
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
