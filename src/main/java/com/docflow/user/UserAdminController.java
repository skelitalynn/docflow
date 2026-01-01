package com.docflow.user;

import com.docflow.audit.AuditLog;
import com.docflow.audit.AuditLogRepository;
import com.docflow.audit.AuditResponse;
import com.docflow.common.PageResponse;
import com.docflow.common.SystemRole;
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

    @PutMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable("id") Long userId,
                                   @Valid @RequestBody UpdateRoleRequest request,
                                   HttpServletRequest httpRequest) {
        User user = userService.updateSystemRole(SecurityUtils.getCurrentUserId(),
                userId,
                request.role(),
                clientIp(httpRequest));
        return UserResponse.from(user);
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
}
