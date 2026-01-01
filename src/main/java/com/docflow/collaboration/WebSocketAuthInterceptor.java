package com.docflow.collaboration;

import com.docflow.common.UserStatus;
import com.docflow.security.AuthTokenService;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {
    private final AuthTokenService tokenService;
    private final UserRepository userRepository;

    public WebSocketAuthInterceptor(AuthTokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        Optional<Long> userId = tokenService.resolveUserId(token);
        if (userId.isEmpty()) {
            return false;
        }
        User user = userRepository.findById(userId.get()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        attributes.put("userId", user.getId());
        attributes.put("nickname", user.getNickname());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String resolveToken(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        String token = headers.getFirst("X-Auth-Token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                String[] pair = part.split("=");
                if (pair.length == 2 && "token".equals(pair[0])) {
                    return pair[1];
                }
            }
        }
        return null;
    }
}
