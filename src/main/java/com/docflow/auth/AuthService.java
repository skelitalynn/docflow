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

// 认证业务：注册/登录/找回/重置密码，负责校验、加密、审计与签发 token。
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

    //注册
    @Transactional
    //事务作用：保证事务创建+审计日志一致性，且确保数据库写入成功后才发token
    //事务保证数据一致性
    public AuthResult register(RegisterRequest request, String ip) {
        // 注册：校验账号唯一性并保存加密密码。
        String email = normalize(request.email());
        String phone = normalize(request.phone());
        //校验账号唯一性
        validateAccount(email, phone);
        //加密密码,不允许明文存储
        String passwordHash = passwordEncoder.encode(request.password());
        //创建用户,默认用户角色为USER,状态为ACTIVE,昵称为请求中的昵称
        User user = User.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordHash)
                .systemRole(SystemRole.USER)
                .status(UserStatus.ACTIVE)
                .nickname(request.nickname())
                .build();
        User saved = userRepository.save(user);
        //持久化 + 审计，注册是敏感操作，进入审计链路
        auditService.record(saved, "register", "user", saved.getId(), null, true, null, ip, null);
        //签发token，许可证
        String token = tokenService.issueToken(saved);
        return new AuthResult(saved, token);
    }

    public AuthResult login(LoginRequest request, String ip) {
        // 登录：校验状态与密码，成功后签发 token。
        //根据账号查找用户，自动区分email/phone，如果找不到则抛出异常
        User user = findByAccount(request.account())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        //校验用户状态，如果状态为非ACTIVE则抛出异常，防止被禁用的账号登陆
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.record(user, "login_fail", "user", user.getId(), null, false, "Account disabled", ip, null);
            throw new AccessDeniedException("Account disabled");
        }
        //校验密码，如果密码不匹配则抛出异常
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(user, "login_fail", "user", user.getId(), null, false, "Invalid credentials", ip, null);
            throw new UnauthorizedException("Invalid credentials");
        }
        //记录审计日志，成功登录
        auditService.record(user, "login_success", "user", user.getId(), null, true, null, ip, null);
        //签发token，许可证
        String token = tokenService.issueToken(user);
        return new AuthResult(user, token);
    }

    public ForgotResult forgot(ForgotRequest request, String ip) {
        // 找回密码：生成一次性 token 并落库（仅返回明文 token）。
        User user = findByAccount(request.account())
                .orElseThrow(() -> new BadRequestException("Account not found"));

        //生成一次性token，UUID，随机
        String rawToken = UUID.randomUUID().toString();
        //hash token，防止泄露
        String tokenHash = tokenHasher.hash(rawToken);
        PasswordReset reset = PasswordReset.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(60))
                .used(false)
                .build();
        passwordResetRepository.save(reset);
        auditService.record(user, "password_forgot", "user", user.getId(), null, true, null, ip, null);
        return new ForgotResult(rawToken, reset.getExpiresAt());
    }

    @Transactional
    public void reset(ResetRequest request, String ip) {
        // 重置密码：验证 token 有效性并更新密码。
        String tokenHash = tokenHasher.hash(request.resetToken());
        //根据tokenHash查找密码重置记录，如果找不到则抛出异常
        PasswordReset reset = passwordResetRepository.findByTokenHashAndUsedFalse(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));
        //校验token是否过期
        if (reset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token expired");
        }
        User user = reset.getUser();
        //更新密码
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        //标记token已使用
        reset.setUsed(true);
        //记录使用时间
        reset.setUsedAt(LocalDateTime.now());
        passwordResetRepository.save(reset);
        auditService.record(user, "password_reset", "user", user.getId(), null, true, null, ip, Map.of());
    }

    // 统一登录入口：自动区分邮箱/手机号。
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

    // 注册校验：至少提供一个账号且保持唯一性。
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

    // 字段标准化：去空格，空值归一化为 null。
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    //record是Java16+的不可变数据类，用于封装结果，避免创建大量临时对象
    //特点：
    // 1. 自动生成构造函数、equals、hashCode、toString
    // 2. 字段默认final，不可变，线程安全
    // 3. 简洁，减少样板代码

    //其实就是用record写DTO，不用手写class+getter/setter+构造函数+equals+hashCode+toString
    //不过复杂的场景还是继续写DTO吧

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
