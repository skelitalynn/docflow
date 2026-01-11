package com.docflow.tag;

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

// Tag APIs: tag CRUD + document tag assignment.
@RestController
@RequestMapping
public class TagController {
    private final TagService tagService;
    private final DocumentTagService documentTagService;

    public TagController(TagService tagService, DocumentTagService documentTagService) {
        this.tagService = tagService;
        this.documentTagService = documentTagService;
    }

    // List current user's tags.
    @GetMapping("/tags")
    public List<TagResponse> list() {
        return tagService.list(SecurityUtils.getCurrentUserId()).stream()
                .map(tag -> new TagResponse(tag.getId(), tag.getName()))
                .toList();
    }

    // Create a tag.
    @PostMapping("/tags")
    public TagResponse create(@Valid @RequestBody CreateTagRequest request, HttpServletRequest httpRequest) {
        Tag tag = tagService.create(SecurityUtils.getCurrentUserId(), request.name(), clientIp(httpRequest));
        return new TagResponse(tag.getId(), tag.getName());
    }

    // Rename a tag.
    @PutMapping("/tags/{id}")
    public TagResponse rename(@PathVariable("id") Long id,
                              @Valid @RequestBody CreateTagRequest request,
                              HttpServletRequest httpRequest) {
        Tag tag = tagService.rename(SecurityUtils.getCurrentUserId(), id, request.name(), clientIp(httpRequest));
        return new TagResponse(tag.getId(), tag.getName());
    }

    // Delete a tag.
    @DeleteMapping("/tags/{id}")
    public MessageResponse delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        tagService.delete(SecurityUtils.getCurrentUserId(), id, clientIp(httpRequest));
        return new MessageResponse("ok");
    }

    // List tags bound to a document.
    @GetMapping("/docs/{id}/tags")
    public List<TagResponse> listDocTags(@PathVariable("id") Long docId) {
        return documentTagService.listTags(docId, SecurityUtils.getCurrentUserId()).stream()
                .map(tag -> new TagResponse(tag.getId(), tag.getName()))
                .toList();
    }

    // Replace tags bound to a document (OWNER/ADMIN only).
    @PutMapping("/docs/{id}/tags")
    public MessageResponse setDocTags(@PathVariable("id") Long docId,
                                      @Valid @RequestBody DocTagRequest request,
                                      HttpServletRequest httpRequest) {
        documentTagService.setTags(docId, SecurityUtils.getCurrentUserId(), request.tagIds(), clientIp(httpRequest));
        return new MessageResponse("ok");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record CreateTagRequest(@NotBlank @Size(max = 64) String name) {
    }

    public record DocTagRequest(List<Long> tagIds) {
    }

    public record TagResponse(Long id, String name) {
    }

    public record MessageResponse(String message) {
    }
}
