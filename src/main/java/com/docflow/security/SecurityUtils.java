package com.docflow.security;

import com.docflow.common.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static UserContext getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserContext user)) {
            throw new UnauthorizedException("Not authenticated");
        }
        return user;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }
}
