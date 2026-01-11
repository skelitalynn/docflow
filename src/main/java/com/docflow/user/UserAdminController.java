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

// 管理端用户管理控制器：用户列表、角色/状态调整、行为分析与审计查询。
// 访问路径位于 /admin/**，权限由 SecurityConfig 限制为 ADMIN。
@RestController
@RequestMapping("/admin/users")
public class UserAdminController {
    private final UserService userService;
    private final AuditLogRepository auditLogRepository;

    public UserAdminController(UserService userService, AuditLogRepository auditLogRepository) {
        this.userService = userService;
        this.auditLogRepository = auditLogRepository;
    }

    // 管理员用户列表：支持关键字/角色/状态筛选与分页返回。
    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(value = "q", required = false) String keyword,
                                                @RequestParam(value = "role", required = false) SystemRole role,
                                                @RequestParam(value = "status", required = false) UserStatus status,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "size", defaultValue = "20") int size) {
        // 关键字会在 Service 中映射到邮箱/手机号/昵称的模糊匹配
        Page<User> users = userService.listUsers(keyword, role, status, PageRequest.of(page, size));
        // 仅输出管理端需要的字段，避免暴露敏感数据
        List<AdminUserResponse> items = users.map(this::toAdminResponse).getContent();
        return new PageResponse<>(items, users.getNumber(), users.getSize(),
                users.getTotalElements(), users.getTotalPages());
    }

    // 调整用户系统角色（管理员权限）。
    @PutMapping("/{id}/role")
    public AdminUserResponse updateRole(@PathVariable("id") Long userId,
                                        @Valid @RequestBody UpdateRoleRequest request,
                                        HttpServletRequest httpRequest) {
        // Service 内部会进行管理员校验并写入审计日志
        User user = userService.updateSystemRole(SecurityUtils.getCurrentUserId(),
                userId,
                request.role(),
                clientIp(httpRequest));
        return toAdminResponse(user);
    }

    // 调整用户状态（启用/冻结/封禁）。
    @PutMapping("/{id}/status")
    public AdminUserResponse updateStatus(@PathVariable("id") Long userId,
                                          @Valid @RequestBody UpdateStatusRequest request,
                                          HttpServletRequest httpRequest) {
        // Service 内部会进行管理员校验并写入审计日志
        User user = userService.updateStatus(SecurityUtils.getCurrentUserId(),
                userId,
                request.status(),
                clientIp(httpRequest));
        return toAdminResponse(user);
    }

    // 行为分析：基于审计日志聚合用户操作统计。
    @GetMapping("/{id}/behavior")
    public UserBehaviorResponse behavior(@PathVariable("id") Long userId) {
        // 行为分析基于审计日志统计，数据来源为 t_audit_log
        userService.getById(userId);
        // 统计该用户的操作总次数
        long total = auditLogRepository.countByActorId(userId);
        // 查询最后一次操作时间，作为最近活跃时间
        LocalDateTime lastActiveAt = auditLogRepository.findLastActiveAt(userId);
        // 按 action 聚合统计，例如 login_success、doc_create 等
        List<AuditLogRepository.ActionCount> counts = auditLogRepository.countActionsByActor(userId);
        List<ActionCountResponse> actions = new ArrayList<>();
        for (AuditLogRepository.ActionCount count : counts) {
            actions.add(new ActionCountResponse(count.getAction(), count.getCount()));
        }
        return new UserBehaviorResponse(userId, total, lastActiveAt, actions);
    }

    // 审计明细：分页返回该用户的操作日志。
    @GetMapping("/{id}/audit")
    public PageResponse<AuditResponse> audit(@PathVariable("id") Long userId,
                                             @RequestParam(value = "page", defaultValue = "0") int page,
                                             @RequestParam(value = "size", defaultValue = "20") int size) {
        // 先校验用户存在，避免查询无效用户
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
