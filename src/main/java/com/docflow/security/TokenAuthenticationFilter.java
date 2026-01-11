package com.docflow.security;

import com.docflow.common.UserStatus;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

// Token 认证过滤器：解析请求中的 token，建立 SecurityContext。
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final AuthTokenService tokenService;
    private final UserRepository userRepository;

    public TokenAuthenticationFilter(AuthTokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<String> tokenOpt = resolveToken(request);
        if (tokenOpt.isPresent()) {
            tokenService.resolveUserId(tokenOpt.get())
                    .flatMap(userRepository::findById)
                    // 账号被禁用时不建立认证上下文。
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        // 兼容标准 Authorization 头与备用 Token 头。
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return Optional.of(authHeader.substring(7));
        }
        String token = request.getHeader("X-Auth-Token");
        if (token != null && !token.isBlank()) {
            return Optional.of(token);
        }
        return Optional.empty();
    }

    private void authenticate(User user) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getSystemRole().name()));
        UserContext context = new UserContext(user.getId(), user.getSystemRole(), user.getNickname());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(context, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
