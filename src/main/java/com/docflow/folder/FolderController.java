package com.docflow.folder;

import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/folders")
public class FolderController {
    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public List<FolderResponse> list() {
        return folderService.list(SecurityUtils.getCurrentUserId()).stream()
                .map(folder -> new FolderResponse(
                        folder.getId(),
                        folder.getName(),
                        folder.getParent() != null ? folder.getParent().getId() : null))
                .toList();
    }

    @PostMapping
    public FolderResponse create(@Valid @RequestBody CreateFolderRequest request, HttpServletRequest httpRequest) {
        Folder folder = folderService.create(SecurityUtils.getCurrentUserId(),
                request.name(),
                request.parentId(),
                clientIp(httpRequest));
        return new FolderResponse(folder.getId(), folder.getName(),
                folder.getParent() != null ? folder.getParent().getId() : null);
    }

    @PutMapping("/{id}")
    public FolderResponse rename(@PathVariable("id") Long id,
                                 @Valid @RequestBody RenameFolderRequest request,
                                 HttpServletRequest httpRequest) {
        Folder folder = folderService.rename(SecurityUtils.getCurrentUserId(), id, request.name(), clientIp(httpRequest));
        return new FolderResponse(folder.getId(), folder.getName(),
                folder.getParent() != null ? folder.getParent().getId() : null);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        folderService.delete(SecurityUtils.getCurrentUserId(), id, clientIp(httpRequest));
        return new MessageResponse("ok");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record CreateFolderRequest(@NotBlank @Size(max = 64) String name,
                                      Long parentId) {
    }

    public record RenameFolderRequest(@NotBlank @Size(max = 64) String name) {
    }

    public record FolderResponse(Long id, String name, Long parentId) {
    }

    public record MessageResponse(String message) {
    }
}
