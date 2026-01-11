package com.docflow.security;

import com.docflow.user.User;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthTokenService {
    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofDays(7);

    public String issueToken(User user) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(user.getId(), Instant.now().plus(ttl)));
        return token;
    }

    public Optional<Long> resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(info.expiresAt())) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(info.userId());
    }

    public void revoke(String token) {
        tokens.remove(token);
    }

    private record TokenInfo(Long userId, Instant expiresAt) {
    }
}
