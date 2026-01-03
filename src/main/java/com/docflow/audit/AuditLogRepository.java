package com.docflow.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByDocumentIdOrderByCreatedAtDesc(Long docId, Pageable pageable);

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

    long countByActorId(Long actorId);

    @Query("select max(l.createdAt) from AuditLog l where l.actor.id = :actorId")
    LocalDateTime findLastActiveAt(@Param("actorId") Long actorId);

    @Query("select l.action as action, count(l) as count from AuditLog l where l.actor.id = :actorId group by l.action")
    List<ActionCount> countActionsByActor(@Param("actorId") Long actorId);

    interface ActionCount {
        String getAction();

        long getCount();
    }
}
