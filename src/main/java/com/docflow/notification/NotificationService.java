package com.docflow.notification;

import com.docflow.comment.Comment;
import com.docflow.common.NotificationType;
import com.docflow.common.NotFoundException;
import com.docflow.document.Document;
import com.docflow.collaboration.WebSocketNotifier;
import com.docflow.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final WebSocketNotifier notifier;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               WebSocketNotifier notifier,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.notifier = notifier;
        this.objectMapper = objectMapper;
    }

    public void notifyShare(User target, Document document, String message) {
        createAndPush(target, document, null, NotificationType.SHARE, message);
    }

    public void notifyCommentReply(User target, Document document, Comment comment, String message) {
        createAndPush(target, document, comment, NotificationType.COMMENT_REPLY, message);
    }

    public void notifyMention(User target, Document document, Comment comment, String message) {
        createAndPush(target, document, comment, NotificationType.MENTION, message);
    }

    public Page<Notification> list(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

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
        String payload = toPayload(message, document, comment);
        Notification notification = Notification.builder()
                .user(target)
                .type(type)
                .document(document)
                .comment(comment)
                .read(false)
                .payload(payload)
                .build();
        Notification saved = notificationRepository.save(notification);
        Map<String, Object> data = new HashMap<>();
        data.put("id", saved.getId());
        data.put("type", saved.getType().name());
        data.put("docId", document != null ? document.getId() : null);
        data.put("commentId", comment != null ? comment.getId() : null);
        data.put("payload", payload);
        notifier.sendToUser(target.getId(), "notification.push", data);
    }

    private String toPayload(String message, Document document, Comment comment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        if (document != null) {
            payload.put("docId", document.getId());
            payload.put("title", document.getTitle());
        }
        if (comment != null) {
            payload.put("commentId", comment.getId());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
