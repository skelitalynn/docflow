package com.docflow.fileshare;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedFileRepository extends JpaRepository<SharedFile, Long> {
    Page<SharedFile> findByDocumentIdOrderByCreatedAtDesc(Long docId, Pageable pageable);
}
