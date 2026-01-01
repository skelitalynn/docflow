package com.docflow.notification;

import com.docflow.common.NotificationType;
import com.docflow.common.PageResponse;
import com.docflow.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse list(@RequestParam(value = "page", defaultValue = "0") int page,
                                         @RequestParam(value = "size", defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Notification> notifications = notificationService.list(userId, PageRequest.of(page, size));
        List<NotificationResponse> items = notifications.map(this::toResponse).getContent();
        long unread = notificationService.unreadCount(userId);
        return new NotificationListResponse(
                new PageResponse<>(items, notifications.getNumber(), notifications.getSize(),
                        notifications.getTotalElements(), notifications.getTotalPages()),
                unread);
    }

    @PostMapping("/read")
    public MessageResponse markRead(@Valid @RequestBody NotificationReadRequest request) {
        notificationService.markRead(SecurityUtils.getCurrentUserId(), request.ids(), request.all());
        return new MessageResponse("ok");
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getDocument() != null ? notification.getDocument().getId() : null,
                notification.getComment() != null ? notification.getComment().getId() : null,
                notification.getPayload(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }

    public record NotificationReadRequest(List<Long> ids, boolean all) {
    }

    public record NotificationResponse(Long id,
                                       NotificationType type,
                                       Long docId,
                                       Long commentId,
                                       String payload,
                                       boolean read,
                                       LocalDateTime createdAt,
                                       LocalDateTime readAt) {
    }

    public record NotificationListResponse(PageResponse<NotificationResponse> page,
                                           long unreadCount) {
    }

    public record MessageResponse(String message) {
    }
}
