package com.docflow.template;

import com.docflow.common.PageResponse;
import com.docflow.document.DocFormat;
import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

// Template APIs: create/list/get/update/delete.
@RestController
@RequestMapping("/templates")
public class DocumentTemplateController {
    private final DocumentTemplateService templateService;

    public DocumentTemplateController(DocumentTemplateService templateService) {
        this.templateService = templateService;
    }

    // Create template.
    @PostMapping
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest request, HttpServletRequest httpRequest) {
        DocumentTemplate template = templateService.create(
                SecurityUtils.getCurrentUserId(),
                request.title(),
                request.description(),
                request.content(),
                request.format(),
                request.isPublic(),
                clientIp(httpRequest));
        return toResponse(template);
    }

    // List accessible templates.
    @GetMapping
    public PageResponse<TemplateResponse> list(@RequestParam(value = "page", defaultValue = "0") int page,
                                               @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<DocumentTemplate> templates = templateService.list(SecurityUtils.getCurrentUserId(), PageRequest.of(page, size));
        List<TemplateResponse> items = templates.map(this::toResponse).getContent();
        return new PageResponse<>(items, templates.getNumber(), templates.getSize(),
                templates.getTotalElements(), templates.getTotalPages());
    }

    // Get template detail (owned or public).
    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable("id") Long id) {
        DocumentTemplate template = templateService.getAccessible(SecurityUtils.getCurrentUserId(), id);
        return toResponse(template);
    }

    // Update template (owner or admin).
    @PutMapping("/{id}")
    public TemplateResponse update(@PathVariable("id") Long id,
                                   @Valid @RequestBody UpdateTemplateRequest request,
                                   HttpServletRequest httpRequest) {
        DocumentTemplate template = templateService.update(
                SecurityUtils.getCurrentUserId(),
                id,
                request.title(),
                request.description(),
                request.content(),
                request.format(),
                request.isPublic(),
                clientIp(httpRequest));
        return toResponse(template);
    }

    // Delete template (owner or admin).
    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        templateService.delete(SecurityUtils.getCurrentUserId(), id, clientIp(httpRequest));
        return new MessageResponse("ok");
    }

    private TemplateResponse toResponse(DocumentTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getTitle(),
                template.getDescription(),
                template.getContent(),
                template.getContentFormat(),
                template.isPublicTemplate(),
                template.getOwner().getId(),
                template.getUpdatedAt()
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record CreateTemplateRequest(@NotBlank @Size(max = 200) String title,
                                        @Size(max = 255) String description,
                                        @NotBlank String content,
                                        DocFormat format,
                                        boolean isPublic) {
    }

    public record UpdateTemplateRequest(@Size(max = 200) String title,
                                        @Size(max = 255) String description,
                                        String content,
                                        DocFormat format,
                                        Boolean isPublic) {
    }

    public record TemplateResponse(Long id,
                                   String title,
                                   String description,
                                   String content,
                                   DocFormat format,
                                   boolean isPublic,
                                   Long ownerId,
                                   LocalDateTime updatedAt) {
    }

    public record MessageResponse(String message) {
    }
}
