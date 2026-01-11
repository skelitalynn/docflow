package com.docflow.security;

import com.docflow.common.SystemRole;

public class UserContext {
    private final Long userId;
    private final SystemRole systemRole;
    private final String nickname;

    public UserContext(Long userId, SystemRole systemRole, String nickname) {
        this.userId = userId;
        this.systemRole = systemRole;
        this.nickname = nickname;
    }

    public Long getUserId() {
        return userId;
    }

    public SystemRole getSystemRole() {
        return systemRole;
    }

    public String getNickname() {
        return nickname;
    }
}
