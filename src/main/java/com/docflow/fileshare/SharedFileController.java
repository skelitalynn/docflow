package com.docflow.fileshare;

import com.docflow.common.PageResponse;
import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping
public class SharedFileController {
    private final SharedFileService fileService;
    private final SharedFileStorageService storageService;

    public SharedFileController(SharedFileService fileService,
                                SharedFileStorageService storageService) {
        this.fileService = fileService;
        this.storageService = storageService;
    }

    @PostMapping("/docs/{id}/files")
    public SharedFileResponse upload(@PathVariable("id") Long docId,
                                     @NotNull @RequestParam("file") MultipartFile file,
                                     HttpServletRequest httpRequest) {
        SharedFile saved = fileService.upload(SecurityUtils.getCurrentUserId(),
                docId,
                file,
                clientIp(httpRequest));
        return toResponse(saved);
    }

    @GetMapping("/docs/{id}/files")
    public PageResponse<SharedFileResponse> list(@PathVariable("id") Long docId,
                                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<SharedFile> files = fileService.list(SecurityUtils.getCurrentUserId(),
                docId,
                PageRequest.of(page, size));
        List<SharedFileResponse> items = files.map(this::toResponse).getContent();
        return new PageResponse<>(items, files.getNumber(), files.getSize(),
                files.getTotalElements(), files.getTotalPages());
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> download(@PathVariable("id") Long fileId) {
        SharedFile file = fileService.get(SecurityUtils.getCurrentUserId(), fileId);
        Path path = storageService.resolvePath(file.getStorageName());
        Resource resource = toResource(path);
        String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalName() + "\"")
                .body(resource);
    }

    private SharedFileResponse toResponse(SharedFile file) {
        return new SharedFileResponse(
                file.getId(),
                file.getDocument().getId(),
                file.getUploader().getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getDownloadUrl(),
                file.getCreatedAt()
        );
    }

    private Resource toResource(Path path) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalStateException("File not found on disk");
            }
            return new UrlResource(path.toUri());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file", ex);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record SharedFileResponse(Long id,
                                     Long docId,
                                     Long uploaderId,
                                     String originalName,
                                     String contentType,
                                     long sizeBytes,
                                     String downloadUrl,
                                     LocalDateTime createdAt) {
    }
}
