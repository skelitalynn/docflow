package com.docflow.task;

import com.docflow.acl.AclService;
import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.DocRole;
import com.docflow.common.NotFoundException;
import com.docflow.common.TaskStatus;
import com.docflow.document.Document;
import com.docflow.document.DocumentRepository;
import com.docflow.notification.NotificationService;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AclService aclService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public TaskService(TaskRepository taskRepository,
                       DocumentRepository documentRepository,
                       UserRepository userRepository,
                       AclService aclService,
                       NotificationService notificationService,
                       AuditService auditService) {
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aclService = aclService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public Task create(Long userId,
                       Long docId,
                       String title,
                       String description,
                       Long assigneeId,
                       LocalDateTime dueAt,
                       String ip) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Task title required");
        }
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        User assignee = null;
        if (assigneeId != null) {
            assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new BadRequestException("Assignee not found"));
        }
        Task task = Task.builder()
                .document(document)
                .creator(creator)
                .assignee(assignee)
                .title(title.trim())
                .description(description)
                .status(TaskStatus.TODO)
                .dueAt(dueAt)
                .build();
        Task saved = taskRepository.save(task);
        Map<String, Object> meta = new HashMap<>();
        meta.put("assigneeId", assigneeId);
        auditService.record(creator, "task_create", "task", saved.getId(), document, true, null, ip, meta);
        if (assignee != null) {
            notificationService.notifyTaskAssigned(assignee, document, saved, "Task assigned");
        }
        return saved;
    }

    @Transactional
    public Task update(Long userId,
                       Long taskId,
                       String title,
                       String description,
                       Long assigneeId,
                       LocalDateTime dueAt,
                       TaskStatus status,
                       String ip) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        Document document = task.getDocument();
        aclService.requireRole(userId, document.getId(), DocRole.EDITOR);
        User operator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Map<String, Object> meta = new HashMap<>();

        if (title != null && !title.isBlank() && !title.equals(task.getTitle())) {
            task.setTitle(title.trim());
            meta.put("title", task.getTitle());
        }
        if (description != null && !description.equals(task.getDescription())) {
            task.setDescription(description);
            meta.put("description", "updated");
        }
        if (dueAt != null && (task.getDueAt() == null || !dueAt.equals(task.getDueAt()))) {
            task.setDueAt(dueAt);
            meta.put("dueAt", dueAt.toString());
        }
        if (assigneeId != null) {
            User newAssignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new BadRequestException("Assignee not found"));
            Long currentId = task.getAssignee() != null ? task.getAssignee().getId() : null;
            if (!newAssignee.getId().equals(currentId)) {
                task.setAssignee(newAssignee);
                meta.put("assigneeId", newAssignee.getId());
                notificationService.notifyTaskAssigned(newAssignee, document, task, "Task assigned");
            }
        }
        boolean statusChanged = false;
        if (status != null && status != task.getStatus()) {
            task.setStatus(status);
            statusChanged = true;
            meta.put("status", status.name());
            if (status == TaskStatus.DONE) {
                task.setCompletedAt(LocalDateTime.now());
            } else {
                task.setCompletedAt(null);
            }
        }

        Task saved = taskRepository.save(task);
        auditService.record(operator, "task_update", "task", saved.getId(), document, true, null, ip, meta);
        if (statusChanged && status == TaskStatus.DONE) {
            notificationService.notifyTaskCompleted(task.getCreator(), document, saved, "Task completed");
            if (task.getAssignee() != null && !task.getAssignee().getId().equals(task.getCreator().getId())) {
                notificationService.notifyTaskCompleted(task.getAssignee(), document, saved, "Task completed");
            }
        }
        return saved;
    }

    public Page<Task> listByDocument(Long userId, Long docId, TaskStatus status, Pageable pageable) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        if (status == null) {
            return taskRepository.findByDocumentIdOrderByCreatedAtDesc(docId, pageable);
        }
        return taskRepository.findByDocumentIdAndStatusOrderByCreatedAtDesc(docId, status, pageable);
    }

    public Page<Task> listAssigned(Long userId, Pageable pageable) {
        return taskRepository.findByAssigneeIdOrderByCreatedAtDesc(userId, pageable);
    }
}
