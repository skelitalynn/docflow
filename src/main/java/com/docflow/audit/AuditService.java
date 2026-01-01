package com.docflow.audit;

import com.docflow.document.Document;
import com.docflow.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(User actor,
                       String action,
                       String targetType,
                       Long targetId,
                       Document document,
                       boolean result,
                       String errorMsg,
                       String ip,
                       Map<String, Object> meta) {
        String metaJson = null;
        if (meta != null && !meta.isEmpty()) {
            try {
                metaJson = objectMapper.writeValueAsString(meta);
            } catch (JsonProcessingException ignored) {
            }
        }
        AuditLog log = AuditLog.builder()
                .actor(actor)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .document(document)
                .result(result)
                .errorMsg(errorMsg)
                .ip(ip)
                .meta(metaJson)
                .build();
        auditLogRepository.save(log);
    }
}
