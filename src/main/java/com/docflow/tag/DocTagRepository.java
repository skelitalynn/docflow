package com.docflow.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Document-tag mapping persistence.
public interface DocTagRepository extends JpaRepository<DocTag, Long> {
    List<DocTag> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    void deleteByTagId(Long tagId);
}
