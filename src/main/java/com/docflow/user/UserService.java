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

    public User updateProfile(Long id, String nickname, String email, String phone, String ip) {
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

    public User updateAvatar(Long id, String avatarUrl, String ip) {
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

    public User updateSystemRole(Long operatorId, Long targetUserId, SystemRole role, String ip) {
        if (role == null) {
            throw new BadRequestException("Role is required");
        }
        User operator = requireAdmin(operatorId);
        User target = getById(targetUserId);
        if (target.getSystemRole() == role) {
            return target;
        }
        SystemRole before = target.getSystemRole();
        target.setSystemRole(role);
        User saved = userRepository.save(target);
        auditService.record(operator, "system_role_update", "user", saved.getId(), null, true, null, ip,
                Map.of("before", before.name(), "after", role.name()));
        return saved;
    }

    public User updateStatus(Long operatorId, Long targetUserId, UserStatus status, String ip) {
        if (status == null) {
            throw new BadRequestException("Status is required");
        }
        User operator = requireAdmin(operatorId);
        User target = getById(targetUserId);
        if (target.getStatus() == status) {
            return target;
        }
        UserStatus before = target.getStatus();
        target.setStatus(status);
        User saved = userRepository.save(target);
        auditService.record(operator, "user_status_update", "user", saved.getId(), null, true, null, ip,
                Map.of("before", before.name(), "after", status.name()));
        return saved;
    }

    public Page<User> listUsers(String keyword, SystemRole role, UserStatus status, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("phone")), like),
                    cb.like(cb.lower(root.get("nickname")), like)
            ));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("systemRole"), role));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private User requireAdmin(Long operatorId) {
        User operator = getById(operatorId);
        if (operator.getSystemRole() != SystemRole.ADMIN) {
            throw new AccessDeniedException("Admin required");
        }
        return operator;
    }
}
