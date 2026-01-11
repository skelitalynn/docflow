package com.docflow.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Version history persistence for list/detail queries.
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    Page<DocumentVersion> findByDocumentId(Long docId, Pageable pageable);

    Page<DocumentVersion> findByDocumentIdAndAutosave(Long docId, boolean autosave, Pageable pageable);

    Optional<DocumentVersion> findByIdAndDocumentId(Long id, Long docId);
}
