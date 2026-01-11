package com.docflow.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByDocumentIdOrderByCreatedAtAsc(Long docId, Pageable pageable);
}
