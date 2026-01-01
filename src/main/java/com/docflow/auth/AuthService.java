package com.docflow.auth;

import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.SystemRole;
import com.docflow.common.UnauthorizedException;
import com.docflow.common.UserStatus;
import com.docflow.security.AuthTokenService;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokenService;
    private final TokenHasher tokenHasher;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordResetRepository passwordResetRepository,
                       PasswordEncoder passwordEncoder,
                       AuthTokenService tokenService,
                       TokenHasher tokenHasher,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.tokenHasher = tokenHasher;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResult register(RegisterRequest request, String ip) {
        String email = normalize(request.email());
        String phone = normalize(request.phone());
        validateAccount(email, phone);
        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordHash)
                .systemRole(SystemRole.USER)
                .status(UserStatus.ACTIVE)
                .nickname(request.nickname())
                .build();
        User saved = userRepository.save(user);
        auditService.record(saved, "register", "user", saved.getId(), null, true, null, ip, null);
        String token = tokenService.issueToken(saved);
        return new AuthResult(saved, token);
    }

    public AuthResult login(LoginRequest request, String ip) {
        User user = findByAccount(request.account())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.record(user, "login_fail", "user", user.getId(), null, false, "Account disabled", ip, null);
            throw new AccessDeniedException("Account disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(user, "login_fail", "user", user.getId(), null, false, "Invalid credentials", ip, null);
            throw new UnauthorizedException("Invalid credentials");
        }
        auditService.record(user, "login_success", "user", user.getId(), null, true, null, ip, null);
        String token = tokenService.issueToken(user);
        return new AuthResult(user, token);
    }

    public ForgotResult forgot(ForgotRequest request, String ip) {
        User user = findByAccount(request.account())
                .orElseThrow(() -> new BadRequestException("Account not found"));
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = tokenHasher.hash(rawToken);
        PasswordReset reset = PasswordReset.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .used(false)
                .build();
        passwordResetRepository.save(reset);
        auditService.record(user, "password_forgot", "user", user.getId(), null, true, null, ip, null);
        return new ForgotResult(rawToken, reset.getExpiresAt());
    }

    @Transactional
    public void reset(ResetRequest request, String ip) {
        String tokenHash = tokenHasher.hash(request.resetToken());
        PasswordReset reset = passwordResetRepository.findByTokenHashAndUsedFalse(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));
        if (reset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token expired");
        }
        User user = reset.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        reset.setUsed(true);
        reset.setUsedAt(LocalDateTime.now());
        passwordResetRepository.save(reset);
        auditService.record(user, "password_reset", "user", user.getId(), null, true, null, ip, Map.of());
    }

    private Optional<User> findByAccount(String account) {
        if (account == null || account.isBlank()) {
            return Optional.empty();
        }
        String normalized = account.trim();
        if (normalized.contains("@")) {
            return userRepository.findByEmail(normalized);
        }
        return userRepository.findByPhone(normalized);
    }

    private void validateAccount(String email, String phone) {
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
            throw new BadRequestException("Email or phone required");
        }
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("Email already registered");
        }
        if (phone != null && userRepository.findByPhone(phone).isPresent()) {
            throw new BadRequestException("Phone already registered");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record AuthResult(User user, String token) {
    }

    public record ForgotResult(String resetToken, LocalDateTime expiresAt) {
    }

    public record RegisterRequest(@Email @Size(max = 128) String email,
                                  @Size(max = 32) String phone,
                                  @NotBlank String password,
                                  @Size(max = 64) String nickname) {
    }

    public record LoginRequest(@NotBlank String account,
                               @NotBlank String password) {
    }

    public record ForgotRequest(@NotBlank String account) {
    }

    public record ResetRequest(@NotBlank String resetToken,
                               @NotBlank String newPassword) {
    }
}
