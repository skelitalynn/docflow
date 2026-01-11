package com.docflow.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

// 审计日志数据访问：提供按文档/用户查询与行为统计能力
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // 按文档查询审计日志（文档维度审计）
    Page<AuditLog> findByDocumentIdOrderByCreatedAtDesc(Long docId, Pageable pageable);

    // 按用户查询审计日志（用户维度审计）
    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

    // 用户操作总次数，用于行为分析
    long countByActorId(Long actorId);

    // 查询用户最后一次操作时间（最近活跃时间）
    @Query("select max(l.createdAt) from AuditLog l where l.actor.id = :actorId")
    LocalDateTime findLastActiveAt(@Param("actorId") Long actorId);

    // 按 action 聚合统计用户行为分布
    @Query("select l.action as action, count(l) as count from AuditLog l where l.actor.id = :actorId group by l.action")
    List<ActionCount> countActionsByActor(@Param("actorId") Long actorId);

    // 行为统计投影：action + count
    interface ActionCount {
        String getAction();

        long getCount();
    }
}
