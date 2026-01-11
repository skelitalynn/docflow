package com.docflow.notification;

import com.docflow.comment.Comment;
import com.docflow.common.NotificationType;
import com.docflow.common.NotFoundException;
import com.docflow.document.Document;
import com.docflow.collaboration.WebSocketNotifier;
import com.docflow.task.Task;
import com.docflow.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationSettingsService settingsService;
    private final WebSocketNotifier notifier;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationSettingsService settingsService,
                               WebSocketNotifier notifier,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.settingsService = settingsService;
        this.notifier = notifier;
        this.objectMapper = objectMapper;
    }

    public void notifyShare(User target, Document document, String message) {
        createAndPush(target, document, (Comment) null, NotificationType.SHARE, message);
    }

    public void notifyCommentReply(User target, Document document, Comment comment, String message) {
        createAndPush(target, document, comment, NotificationType.COMMENT_REPLY, message);
    }

    public void notifyMention(User target, Document document, Comment comment, String message) {
        createAndPush(target, document, comment, NotificationType.MENTION, message);
    }

    public void notifyTaskAssigned(User target, Document document, Task task, String message) {
        createAndPush(target, document, task, NotificationType.TASK_ASSIGNED, message);
    }

    public void notifyTaskCompleted(User target, Document document, Task task, String message) {
        createAndPush(target, document, task, NotificationType.TASK_COMPLETED, message);
    }

    public void notifyDocumentEdited(User target, Document document, String message) {
        createAndPush(target, document, (Comment) null, NotificationType.DOC_EDIT, message);
    }

    public void notifyComment(User target, Document document, Comment comment, String message) {
        createAndPush(target, document, comment, NotificationType.COMMENT, message);
    }

    public void notifyCommentStatus(User target, Document document, Comment comment, String message) {
        createAndPush(target, document, comment, NotificationType.COMMENT_STATUS, message);
    }

    //查通知列表
    public Page<Notification> list(Long userId,
                                   NotificationType type,
                                   Boolean read,
                                   LocalDateTime from,
                                   LocalDateTime to,
                                   Pageable pageable) {
        Specification<Notification> spec = (root, query, cb) ->
                cb.equal(root.get("user").get("id"), userId);
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (read != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("read"), read));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return notificationRepository.findAll(spec, pageable);
    }

    //小红点——未读通知
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    //标记已读
    @Transactional
    public void markRead(Long userId, List<Long> ids, boolean all) {
        if (all) {
            Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged());
            for (Notification notification : page) {
                if (!notification.isRead()) {
                    notification.setRead(true);
                    notification.setReadAt(LocalDateTime.now());
                }
            }
            return;
        }
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            Notification notification = notificationRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Notification not found"));
            if (!notification.getUser().getId().equals(userId)) {
                throw new NotFoundException("Notification not found");
            }
            if (!notification.isRead()) {
                notification.setRead(true);
                notification.setReadAt(LocalDateTime.now());
                notificationRepository.save(notification);
            }
        }
    }

    private void createAndPush(User target,
                               Document document,
                               Comment comment,
                               NotificationType type,
                               String message) {
        createAndPush(target, document, comment, null, type, message);
    }

    private void createAndPush(User target,
                               Document document,
                               Task task,
                               NotificationType type,
                               String message) {
        createAndPush(target, document, null, task, type, message);
    }

    private void createAndPush(User target,
                               Document document,
                               Comment comment,
                               Task task,
                               NotificationType type,
                               String message) {
        // 先尊重用户通知设置，再落库 + 推送。
        if (target == null || !settingsService.isEnabled(target.getId(), type)) {
            return;
        }
        //打包要展示的信息
        String payload = toPayload(message, document, comment, task);
        Notification notification = Notification.builder()
                .user(target)
                .type(type)
                .document(document)
                .comment(comment)
                .task(task)
                .read(false)
                .payload(payload)
                .build();
        Notification saved = notificationRepository.save(notification);
        Map<String, Object> data = new HashMap<>();
        data.put("id", saved.getId());
        data.put("type", saved.getType().name());
        data.put("docId", document != null ? document.getId() : null);
        data.put("commentId", comment != null ? comment.getId() : null);
        data.put("taskId", task != null ? task.getId() : null);
        data.put("payload", payload);
        notifier.sendToUser(target.getId(), "notification.push", data);
    }

    //将所有类型的通知统一生成一份JSON，前端拿到就能直接展示
    private String toPayload(String message, Document document, Comment comment, Task task) {
        // 统一 JSON payload，方便前端展示与跳转。
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        if (document != null) {
            payload.put("docId", document.getId());
            payload.put("title", document.getTitle());
        }
        if (comment != null) {
            payload.put("commentId", comment.getId());
            if (comment.getStatus() != null) {
                payload.put("commentStatus", comment.getStatus().name());
            }
        }
        if (task != null) {
            payload.put("taskId", task.getId());
            payload.put("taskTitle", task.getTitle());
            payload.put("status", task.getStatus().name());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
