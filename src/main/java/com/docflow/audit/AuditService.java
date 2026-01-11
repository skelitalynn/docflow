package com.docflow.audit;

import com.docflow.document.Document;
import com.docflow.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

// 审计服务：统一写入操作日志，供追溯与行为分析使用
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
        // 将附加信息序列化为 JSON，便于后续检索与统计
        String metaJson = null;
        if (meta != null && !meta.isEmpty()) {
            try {
                metaJson = objectMapper.writeValueAsString(meta);
            } catch (JsonProcessingException ignored) {
            }
        }
        // 构建审计日志实体并落库
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
