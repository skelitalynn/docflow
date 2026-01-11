package com.docflow.user;

import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.NotFoundException;
import com.docflow.common.SystemRole;
import com.docflow.common.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// 用户资料与管理员操作：资料更新、角色/状态管理、用户列表。
// 说明：管理员相关能力会在 Service 内部执行权限校验与审计记录。
@Service
public class UserService {
    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    // 个人资料更新：字段级校验与唯一性检查，并写入审计日志。
    public User updateProfile(Long id, String nickname, String email, String phone, String ip) {
        // 仅更新传入且发生变化的字段。
        User user = getById(id);
        Map<String, Object> changes = new HashMap<>();
        if (nickname != null) {
            if (nickname.isBlank()) {
                throw new BadRequestException("Nickname cannot be blank");
            }
            if (nickname.length() > 64) {
                throw new BadRequestException("Nickname too long");
            }
            if (!Objects.equals(nickname, user.getNickname())) {
                user.setNickname(nickname);
                changes.put("nickname", nickname);
            }
        }
        if (email != null) {
            String normalized = normalize(email);
            if (normalized == null) {
                throw new BadRequestException("Email cannot be blank");
            }
            if (!Objects.equals(normalized, user.getEmail())) {
                ensureEmailAvailable(user.getId(), normalized);
                user.setEmail(normalized);
                changes.put("email", normalized);
            }
        }
        if (phone != null) {
            String normalized = normalize(phone);
            if (normalized == null) {
                throw new BadRequestException("Phone cannot be blank");
            }
            if (!Objects.equals(normalized, user.getPhone())) {
                ensurePhoneAvailable(user.getId(), normalized);
                user.setPhone(normalized);
                changes.put("phone", normalized);
            }
        }
        if (changes.isEmpty()) {
            return user;
        }
        User saved = userRepository.save(user);
        auditService.record(saved, "profile_update", "user", saved.getId(), null, true, null, ip, changes);
        return saved;
    }

    // 头像更新：仅更新 URL，记录变更审计。
    public User updateAvatar(Long id, String avatarUrl, String ip) {
        // 头像上传结果只保存 URL。
        if (avatarUrl == null || avatarUrl.isBlank()) {
            throw new BadRequestException("Avatar URL required");
        }
        User user = getById(id);
        if (Objects.equals(avatarUrl, user.getAvatarUrl())) {
            return user;
        }
        user.setAvatarUrl(avatarUrl);
        User saved = userRepository.save(user);
        auditService.record(saved, "avatar_update", "user", saved.getId(), null, true, null, ip,
                Map.of("avatarUrl", avatarUrl));
        return saved;
    }

    // 系统角色调整：仅管理员可操作。
    public User updateSystemRole(Long operatorId, Long targetUserId, SystemRole role, String ip) {
        // 角色为空直接拒绝，避免无效更新
        if (role == null) {
            throw new BadRequestException("Role is required");
        }
        // 系统角色调整仅管理员可操作（权限校验）
        User operator = requireAdmin(operatorId);
        User target = getById(targetUserId);
        if (target.getSystemRole() == role) {
            // 角色未变化则直接返回，避免无意义写库
            return target;
        }
        SystemRole before = target.getSystemRole();
        target.setSystemRole(role);
        User saved = userRepository.save(target);
        // 记录审计日志，便于后续追溯与行为分析
        auditService.record(operator, "system_role_update", "user", saved.getId(), null, true, null, ip,
                Map.of("before", before.name(), "after", role.name()));
        return saved;
    }

    // 账号状态调整：仅管理员可操作（ACTIVE/FROZEN/BANNED）。
    public User updateStatus(Long operatorId, Long targetUserId, UserStatus status, String ip) {
        // 状态为空直接拒绝
        if (status == null) {
            throw new BadRequestException("Status is required");
        }
        // 账号状态调整仅管理员可操作（权限校验）
        User operator = requireAdmin(operatorId);
        User target = getById(targetUserId);
        if (target.getStatus() == status) {
            // 状态未变化则直接返回
            return target;
        }
        UserStatus before = target.getStatus();
        target.setStatus(status);
        User saved = userRepository.save(target);
        // 状态变更写入审计日志
        auditService.record(operator, "user_status_update", "user", saved.getId(), null, true, null, ip,
                Map.of("before", before.name(), "after", status.name()));
        return saved;
    }

    // 管理员用户列表：支持关键字与角色/状态筛选。
    public Page<User> listUsers(String keyword, SystemRole role, UserStatus status, Pageable pageable) {
        // 使用 JPA Specification 动态拼装 where 条件，便于多条件组合
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (keyword != null && !keyword.isBlank()) {
            // 关键字支持邮箱/手机号/昵称模糊查询
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("phone")), like),
                    cb.like(cb.lower(root.get("nickname")), like)
            ));
        }
        if (role != null) {
            // 系统角色过滤
            spec = spec.and((root, query, cb) -> cb.equal(root.get("systemRole"), role));
        }
        if (status != null) {
            // 用户状态过滤
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        // PageRequest 负责分页与排序，Repository 直接返回 Page
        return userRepository.findAll(spec, pageable);
    }

    private void ensureEmailAvailable(Long userId, String email) {
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BadRequestException("Email already registered");
                });
    }

    private void ensurePhoneAvailable(Long userId, String phone) {
        userRepository.findByPhone(phone)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BadRequestException("Phone already registered");
                });
    }

    //标准化字符串,去除空格,如果为空则返回null,否则返回trim后的字符串
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // 要求操作员为管理员，否则抛出异常，用于保护管理端能力
    private User requireAdmin(Long operatorId) {
        User operator = getById(operatorId);
        if (operator.getSystemRole() != SystemRole.ADMIN) {
            throw new AccessDeniedException("Admin required");
        }
        return operator;
    }
}
