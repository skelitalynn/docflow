package com.docflow.comment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {
}
