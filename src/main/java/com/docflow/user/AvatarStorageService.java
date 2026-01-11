package com.docflow.user;

import com.docflow.common.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// 头像文件存储：限制大小/类型并保存到本地 uploads/avatars。
@Service
public class AvatarStorageService {
    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final Map<String, String> EXT_BY_CONTENT = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );
    private final Path baseDir = Paths.get("uploads", "avatars");

    public String store(MultipartFile file) {
        // 限制大小与类型，避免非法文件上传。
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Avatar file required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Avatar file too large");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BadRequestException("Avatar must be an image");
        }
        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(filename).normalize();
            // 防止路径穿越写入。
            if (!target.startsWith(baseDir)) {
                throw new BadRequestException("Invalid file path");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store avatar", ex);
        }
        return "/avatars/" + filename;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot > 0 && dot < originalFilename.length() - 1) {
                String ext = originalFilename.substring(dot).toLowerCase(Locale.ROOT);
                if (ext.matches("\\.[a-z0-9]+")) {
                    return ext;
                }
            }
        }
        return EXT_BY_CONTENT.getOrDefault(contentType.toLowerCase(Locale.ROOT), "");
    }
}
