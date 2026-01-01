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

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthResult result = authService.register(request, clientIp(httpRequest));
        return new AuthResponse(result.token(), UserResponse.from(result.user()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResult result = authService.login(request, clientIp(httpRequest));
        return new AuthResponse(result.token(), UserResponse.from(result.user()));
    }

    @PostMapping("/forgot")
    public ForgotResponse forgot(@Valid @RequestBody ForgotRequest request, HttpServletRequest httpRequest) {
        ForgotResult result = authService.forgot(request, clientIp(httpRequest));
        return new ForgotResponse(result.resetToken(), result.expiresAt());
    }

    @PostMapping("/reset")
    public MessageResponse reset(@Valid @RequestBody ResetRequest request, HttpServletRequest httpRequest) {
        authService.reset(request, clientIp(httpRequest));
        return new MessageResponse("ok");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record AuthResponse(String token, UserResponse user) {
    }

    public record ForgotResponse(String resetToken, LocalDateTime expiresAt) {
    }

    public record MessageResponse(String message) {
    }
}
