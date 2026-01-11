package com.docflow.acl;

import com.docflow.common.DocRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocMemberRepository extends JpaRepository<DocMember, Long> {
    Optional<DocMember> findByDocumentIdAndUserId(Long docId, Long userId);

    List<DocMember> findByDocumentId(Long docId);

    long countByDocumentIdAndRole(Long docId, DocRole role);

    @org.springframework.data.jpa.repository.Query("""
            select m from DocMember m
            join fetch m.user
            where m.document.id = :docId
            """)
    List<DocMember> findWithUserByDocumentId(@org.springframework.data.repository.query.Param("docId") Long docId);
}
