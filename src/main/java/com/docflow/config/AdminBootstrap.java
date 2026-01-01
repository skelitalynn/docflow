package com.docflow.config;

import com.docflow.audit.AuditService;
import com.docflow.common.SystemRole;
import com.docflow.common.UserStatus;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${docflow.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${docflow.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${docflow.bootstrap.admin-phone:}")
    private String adminPhone;

    @Value("${docflow.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${docflow.bootstrap.admin-nickname:admin}")
    private String adminNickname;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        String email = normalize(adminEmail);
        String phone = normalize(adminPhone);
        if ((email == null && phone == null) || isBlank(adminPassword)) {
            return;
        }
        if (userRepository.existsBySystemRole(SystemRole.ADMIN)) {
            return;
        }
        User user = userRepository.findByEmailOrPhone(email, phone).orElse(null);
        if (user == null) {
            user = User.builder()
                    .email(email)
                    .phone(phone)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .systemRole(SystemRole.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .nickname(adminNickname)
                    .build();
        } else {
            user.setSystemRole(SystemRole.ADMIN);
            if (isBlank(user.getNickname())) {
                user.setNickname(adminNickname);
            }
        }
        User saved = userRepository.save(user);
        auditService.record(saved, "bootstrap_admin", "user", saved.getId(), null, true, null, null, null);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
