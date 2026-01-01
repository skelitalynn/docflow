package com.docflow.template;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    @Query("""
            select t from DocumentTemplate t
            where t.id = :id
              and (t.owner.id = :userId or t.publicTemplate = true)
            """)
    Optional<DocumentTemplate> findAccessibleById(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            select t from DocumentTemplate t
            where t.owner.id = :userId
               or t.publicTemplate = true
            """)
    Page<DocumentTemplate> findAccessible(@Param("userId") Long userId, Pageable pageable);
}
