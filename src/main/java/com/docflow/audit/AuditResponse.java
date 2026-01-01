package com.docflow.audit;

import java.time.LocalDateTime;

public record AuditResponse(Long id,
                            Long actorId,
                            String action,
                            String targetType,
                            Long targetId,
                            boolean result,
                            String errorMsg,
                            String ip,
                            String meta,
                            LocalDateTime createdAt) {
}
