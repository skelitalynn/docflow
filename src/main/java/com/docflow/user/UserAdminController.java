package com.docflow.user;

import com.docflow.audit.AuditLog;
import com.docflow.audit.AuditLogRepository;
import com.docflow.audit.AuditResponse;
import com.docflow.common.PageResponse;
import com.docflow.common.SystemRole;
import com.docflow.common.UserStatus;
import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class UserAdminController {
    private final UserService userService;
    private final AuditLogRepository auditLogRepository;

    public UserAdminController(UserService userService, AuditLogRepository auditLogRepository) {
        this.userService = userService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(value = "q", required = false) String keyword,
                                                @RequestParam(value = "role", required = false) SystemRole role,
                                                @RequestParam(value = "status", required = false) UserStatus status,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<User> users = userService.listUsers(keyword, role, status, PageRequest.of(page, size));
        List<AdminUserResponse> items = users.map(this::toAdminResponse).getContent();
        return new PageResponse<>(items, users.getNumber(), users.getSize(),
                users.getTotalElements(), users.getTotalPages());
    }

    @PutMapping("/{id}/role")
    public AdminUserResponse updateRole(@PathVariable("id") Long userId,
                                        @Valid @RequestBody UpdateRoleRequest request,
                                        HttpServletRequest httpRequest) {
        User user = userService.updateSystemRole(SecurityUtils.getCurrentUserId(),
                userId,
                request.role(),
                clientIp(httpRequest));
        return toAdminResponse(user);
    }

    @PutMapping("/{id}/status")
    public AdminUserResponse updateStatus(@PathVariable("id") Long userId,
                                          @Valid @RequestBody UpdateStatusRequest request,
                                          HttpServletRequest httpRequest) {
        User user = userService.updateStatus(SecurityUtils.getCurrentUserId(),
                userId,
                request.status(),
                clientIp(httpRequest));
        return toAdminResponse(user);
    }

    @GetMapping("/{id}/behavior")
    public UserBehaviorResponse behavior(@PathVariable("id") Long userId) {
        userService.getById(userId);
        long total = auditLogRepository.countByActorId(userId);
        LocalDateTime lastActiveAt = auditLogRepository.findLastActiveAt(userId);
        List<AuditLogRepository.ActionCount> counts = auditLogRepository.countActionsByActor(userId);
        List<ActionCountResponse> actions = new ArrayList<>();
        for (AuditLogRepository.ActionCount count : counts) {
            actions.add(new ActionCountResponse(count.getAction(), count.getCount()));
        }
        return new UserBehaviorResponse(userId, total, lastActiveAt, actions);
    }

    @GetMapping("/{id}/audit")
    public PageResponse<AuditResponse> audit(@PathVariable("id") Long userId,
                                             @RequestParam(value = "page", defaultValue = "0") int page,
                                             @RequestParam(value = "size", defaultValue = "20") int size) {
        userService.getById(userId);
        Page<AuditLog> logs = auditLogRepository.findByActorIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<AuditResponse> items = logs.map(this::toResponse).getContent();
        return new PageResponse<>(items, logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    private AuditResponse toResponse(AuditLog log) {
        return new AuditResponse(
                log.getId(),
                log.getActor().getId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.isResult(),
                log.getErrorMsg(),
                log.getIp(),
                log.getMeta(),
                log.getCreatedAt()
        );
    }

    public record UpdateRoleRequest(@NotNull SystemRole role) {
    }

    public record UpdateStatusRequest(@NotNull UserStatus status) {
    }

    public record AdminUserResponse(Long id,
                                    String email,
                                    String phone,
                                    String nickname,
                                    String avatarUrl,
                                    SystemRole systemRole,
                                    UserStatus status,
                                    LocalDateTime createdAt,
                                    LocalDateTime updatedAt) {
    }

    public record ActionCountResponse(String action, long count) {
    }

    public record UserBehaviorResponse(Long userId,
                                       long totalActions,
                                       LocalDateTime lastActiveAt,
                                       List<ActionCountResponse> actions) {
    }

    private AdminUserResponse toAdminResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getSystemRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
