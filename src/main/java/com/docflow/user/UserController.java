package com.docflow.user;

import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 个人资料入口：查询/更新资料与头像。
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final AvatarStorageService avatarStorageService;

    public UserController(UserService userService, AvatarStorageService avatarStorageService) {
        this.userService = userService;
        this.avatarStorageService = avatarStorageService;
    }

    //获取当前用户信息（仅当前登录用户）。
    @GetMapping("/me")
    public UserResponse me() {
        User user = userService.getById(SecurityUtils.getCurrentUserId());
        return UserResponse.from(user);
    }
    
    //更新当前用户信息：仅更新传入的昵称/邮箱/手机号。
    @PutMapping("/me")
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
        User user = userService.updateProfile(
                SecurityUtils.getCurrentUserId(),
                request.nickname(),
                request.email(),
                request.phone(),
                clientIp(httpRequest));
        return UserResponse.from(user);
    }
    
    //上传头像：保存文件并回写头像 URL。
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest httpRequest) {
        // 先存储文件，再回写头像 URL。
        String avatarUrl = avatarStorageService.store(file);
        User user = userService.updateAvatar(SecurityUtils.getCurrentUserId(), avatarUrl, clientIp(httpRequest));
        return UserResponse.from(user);
    }

    //获取客户端IP
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    //更新当前用户信息请求
    public record UpdateProfileRequest(@Email @Size(max = 128) String email,
                                       @Size(max = 32) String phone,
                                       @Size(max = 64) String nickname) {
    }
}
