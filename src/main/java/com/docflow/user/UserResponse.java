package com.docflow.user;

import com.docflow.common.SystemRole;

public record UserResponse(Long id,
                           String email,
                           String phone,
                           String nickname,
                           String avatarUrl,
                           SystemRole systemRole) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getSystemRole());
    }
}
