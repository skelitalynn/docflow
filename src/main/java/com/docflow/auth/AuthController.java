package com.docflow.auth;

import com.docflow.auth.AuthService.AuthResult;
import com.docflow.auth.AuthService.ForgotResult;
import com.docflow.auth.AuthService.ForgotRequest;
import com.docflow.auth.AuthService.LoginRequest;
import com.docflow.auth.AuthService.RegisterRequest;
import com.docflow.auth.AuthService.ResetRequest;
import com.docflow.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

// 认证入口：注册/登录/找回/重置，统一输出 token 与基础用户信息。
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 注册：创建账号并签发 token，前端可直接进入系统。
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthResult result = authService.register(request, clientIp(httpRequest));
        return new AuthResponse(result.token(), UserResponse.from(result.user()));
    }

    // 登录：校验账号密码并签发 token。
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResult result = authService.login(request, clientIp(httpRequest));
        return new AuthResponse(result.token(), UserResponse.from(result.user()));
    }

    //找回密码
    @PostMapping("/forgot")
    public ForgotResponse forgot(@Valid @RequestBody ForgotRequest request, HttpServletRequest httpRequest) {
        ForgotResult result = authService.forgot(request, clientIp(httpRequest));
        return new ForgotResponse(result.resetToken(), result.expiresAt());
    }

    //重置密码
    @PostMapping("/reset")
    public MessageResponse reset(@Valid @RequestBody ResetRequest request, HttpServletRequest httpRequest) {
        authService.reset(request, clientIp(httpRequest));
        return new MessageResponse("ok");
    }

    //获取客户端IP
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    //响应
    public record AuthResponse(String token, UserResponse user) {
    }
    //重置密码响应
    public record ForgotResponse(String resetToken, LocalDateTime expiresAt) {
    }

    //消息响应
    public record MessageResponse(String message) {
    }
}
