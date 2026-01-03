package com.docflow.task;

import com.docflow.common.PageResponse;
import com.docflow.common.TaskStatus;
import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/docs/{id}/tasks")
    public TaskResponse create(@PathVariable("id") Long docId,
                               @Valid @RequestBody CreateTaskRequest request,
                               HttpServletRequest httpRequest) {
        Task task = taskService.create(SecurityUtils.getCurrentUserId(),
                docId,
                request.title(),
                request.description(),
                request.assigneeId(),
                request.dueAt(),
                clientIp(httpRequest));
        return toResponse(task);
    }

    @GetMapping("/docs/{id}/tasks")
    public PageResponse<TaskResponse> listByDocument(@PathVariable("id") Long docId,
                                                     @RequestParam(value = "status", required = false) TaskStatus status,
                                                     @RequestParam(value = "page", defaultValue = "0") int page,
                                                     @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Task> tasks = taskService.listByDocument(SecurityUtils.getCurrentUserId(),
                docId,
                status,
                PageRequest.of(page, size));
        List<TaskResponse> items = tasks.map(this::toResponse).getContent();
        return new PageResponse<>(items, tasks.getNumber(), tasks.getSize(), tasks.getTotalElements(), tasks.getTotalPages());
    }

    @GetMapping("/tasks/assigned")
    public PageResponse<TaskResponse> listAssigned(@RequestParam(value = "page", defaultValue = "0") int page,
                                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Task> tasks = taskService.listAssigned(SecurityUtils.getCurrentUserId(), PageRequest.of(page, size));
        List<TaskResponse> items = tasks.map(this::toResponse).getContent();
        return new PageResponse<>(items, tasks.getNumber(), tasks.getSize(), tasks.getTotalElements(), tasks.getTotalPages());
    }

    @PutMapping("/tasks/{id}")
    public TaskResponse update(@PathVariable("id") Long taskId,
                               @Valid @RequestBody UpdateTaskRequest request,
                               HttpServletRequest httpRequest) {
        Task task = taskService.update(SecurityUtils.getCurrentUserId(),
                taskId,
                request.title(),
                request.description(),
                request.assigneeId(),
                request.dueAt(),
                request.status(),
                clientIp(httpRequest));
        return toResponse(task);
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getDocument().getId(),
                task.getCreator().getId(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueAt(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record CreateTaskRequest(@NotBlank @Size(max = 200) String title,
                                    String description,
                                    Long assigneeId,
                                    LocalDateTime dueAt) {
    }

    public record UpdateTaskRequest(@Size(max = 200) String title,
                                    String description,
                                    Long assigneeId,
                                    LocalDateTime dueAt,
                                    TaskStatus status) {
    }

    public record TaskResponse(Long id,
                               Long docId,
                               Long creatorId,
                               Long assigneeId,
                               String title,
                               String description,
                               TaskStatus status,
                               LocalDateTime dueAt,
                               LocalDateTime completedAt,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
    }
}
