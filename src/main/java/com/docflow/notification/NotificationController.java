package com.docflow.notification;

import com.docflow.common.NotificationType;
import com.docflow.common.PageResponse;
import com.docflow.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationSettingsService settingsService;

    public NotificationController(NotificationService notificationService,
                                  NotificationSettingsService settingsService) {
        this.notificationService = notificationService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public NotificationListResponse list(@RequestParam(value = "page", defaultValue = "0") int page,
                                         @RequestParam(value = "size", defaultValue = "20") int size,
                                         @RequestParam(value = "type", required = false) NotificationType type,
                                         @RequestParam(value = "read", required = false) Boolean read,
                                         @RequestParam(value = "from", required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                         @RequestParam(value = "to", required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Notification> notifications = notificationService.list(userId, type, read, from, to,
                PageRequest.of(page, size));
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

    @GetMapping("/settings")
    public NotificationSettingsSnapshot settings() {
        return settingsService.getSettings(SecurityUtils.getCurrentUserId());
    }

    @PutMapping("/settings")
    public NotificationSettingsSnapshot updateGlobal(@Valid @RequestBody NotificationMuteRequest request) {
        return settingsService.updateMuteAll(SecurityUtils.getCurrentUserId(), request.muteAll());
    }

    @PutMapping("/settings/{type}")
    public NotificationSettingsSnapshot updateType(@PathVariable("type") NotificationType type,
                                                   @Valid @RequestBody NotificationTypeSettingRequest request) {
        return settingsService.updateType(SecurityUtils.getCurrentUserId(), type, request.enabled());
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getDocument() != null ? notification.getDocument().getId() : null,
                notification.getComment() != null ? notification.getComment().getId() : null,
                notification.getTask() != null ? notification.getTask().getId() : null,
                notification.getPayload(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }

    public record NotificationReadRequest(List<Long> ids, boolean all) {
    }

    public record NotificationMuteRequest(@NotNull Boolean muteAll) {
    }

    public record NotificationTypeSettingRequest(@NotNull Boolean enabled) {
    }

    public record NotificationResponse(Long id,
                                       NotificationType type,
                                       Long docId,
                                       Long commentId,
                                       Long taskId,
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
