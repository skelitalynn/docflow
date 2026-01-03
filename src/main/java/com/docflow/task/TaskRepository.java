package com.docflow.task;

import com.docflow.common.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByDocumentIdOrderByCreatedAtDesc(Long docId, Pageable pageable);

    Page<Task> findByDocumentIdAndStatusOrderByCreatedAtDesc(Long docId, TaskStatus status, Pageable pageable);

    Page<Task> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId, Pageable pageable);
}
