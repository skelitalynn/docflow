package com.docflow.user;

import com.docflow.common.SystemRole;

//不可变数据类，用于封装用户信息，返回给前端的DTO，避免把passwordHash等敏感字段暴露出去
//特点：
// 1. 自动生成构造函数、equals、hashCode、toString
// 2. 字段默认final，不可变，线程安全
// 3. 简洁，减少样板代码

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
