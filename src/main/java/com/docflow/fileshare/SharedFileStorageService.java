package com.docflow.fileshare;

import com.docflow.common.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

// 共享文件的本地存储实现：保存到 uploads/shared 目录。
@Service
public class SharedFileStorageService {
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private final Path baseDir = Paths.get("uploads", "shared");

    public StoredFile store(MultipartFile file) {
        // 基础校验：空文件/超出大小直接拒绝。
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("File too large");
        }
        // 生成存储文件名，避免重名覆盖。
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String storageName = UUID.randomUUID() + extractExtension(originalName);
        try {
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(storageName).normalize();
            // 防目录穿越：确保最终路径仍在 baseDir 之下。
            if (!target.startsWith(baseDir)) {
                throw new BadRequestException("Invalid file path");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(originalName, storageName, file.getContentType(), file.getSize());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store file", ex);
        }
    }

    public Path resolvePath(String storageName) {
        // 下载时校验存储名并防目录穿越。
        if (storageName == null || storageName.isBlank()) {
            throw new BadRequestException("Storage name required");
        }
        Path target = baseDir.resolve(storageName).normalize();
        if (!target.startsWith(baseDir)) {
            throw new BadRequestException("Invalid file path");
        }
        return target;
    }

    private String extractExtension(String filename) {
        // 只保留合理长度的后缀，避免异常文件名。
        int dot = filename.lastIndexOf('.');
        if (dot > 0 && dot < filename.length() - 1) {
            String ext = filename.substring(dot);
            if (ext.length() <= 10) {
                return ext;
            }
        }
        return "";
    }

    public record StoredFile(String originalName,
                             String storageName,
                             String contentType,
                             long sizeBytes) {
    }
}
