package com.docflow.template;

import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.NotFoundException;
import com.docflow.common.SystemRole;
import com.docflow.document.DocFormat;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

// Template domain: create/update/delete with owner/admin guard.
@Service
public class DocumentTemplateService {
    private final DocumentTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public DocumentTemplateService(DocumentTemplateRepository templateRepository,
                                   UserRepository userRepository,
                                   AuditService auditService) {
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // List accessible templates (own + public).
    public Page<DocumentTemplate> list(Long userId, Pageable pageable) {
        return templateRepository.findAccessible(userId, pageable);
    }

    // Fetch a template if it is owned or public.
    public DocumentTemplate getAccessible(Long userId, Long templateId) {
        return templateRepository.findAccessibleById(templateId, userId)
                .orElseThrow(() -> new NotFoundException("Template not found"));
    }

    // Create a new template with chosen format.
    @Transactional
    public DocumentTemplate create(Long userId,
                                   String title,
                                   String description,
                                   String content,
                                   DocFormat format,
                                   boolean isPublic,
                                   String ip) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Title is required");
        }
        if (content == null) {
            throw new BadRequestException("Content is required");
        }
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        DocFormat resolvedFormat = format == null ? DocFormat.RICH_TEXT : format;
        DocumentTemplate template = DocumentTemplate.builder()
                .owner(owner)
                .title(title.trim())
                .description(description)
                .content(content)
                .contentFormat(resolvedFormat)
                .publicTemplate(isPublic)
                .build();
        DocumentTemplate saved = templateRepository.save(template);
        auditService.record(owner, "template_create", "template", saved.getId(), null, true, null, ip, null);
        return saved;
    }

    // Update template fields (owner or admin only).
    @Transactional
    public DocumentTemplate update(Long userId,
                                   Long templateId,
                                   String title,
                                   String description,
                                   String content,
                                   DocFormat format,
                                   Boolean isPublic,
                                   String ip) {
        DocumentTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        User operator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        ensureOwnerOrAdmin(operator, template);

        if (title != null && !title.isBlank() && !Objects.equals(template.getTitle(), title)) {
            template.setTitle(title.trim());
        }
        if (description != null && !Objects.equals(template.getDescription(), description)) {
            template.setDescription(description);
        }
        if (content != null && !Objects.equals(template.getContent(), content)) {
            template.setContent(content);
        }
        if (format != null && format != template.getContentFormat()) {
            template.setContentFormat(format);
        }
        if (isPublic != null && isPublic != template.isPublicTemplate()) {
            template.setPublicTemplate(isPublic);
        }

        DocumentTemplate saved = templateRepository.save(template);
        auditService.record(operator, "template_update", "template", saved.getId(), null, true, null, ip,
                Map.of("templateId", saved.getId()));
        return saved;
    }

    // Delete template (owner or admin only).
    @Transactional
    public void delete(Long userId, Long templateId, String ip) {
        DocumentTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        User operator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        ensureOwnerOrAdmin(operator, template);
        templateRepository.delete(template);
        auditService.record(operator, "template_delete", "template", templateId, null, true, null, ip, null);
    }

    // Shared permission guard for template write operations.
    private void ensureOwnerOrAdmin(User operator, DocumentTemplate template) {
        if (operator.getSystemRole() == SystemRole.ADMIN) {
            return;
        }
        if (!template.getOwner().getId().equals(operator.getId())) {
            throw new AccessDeniedException("No template access");
        }
    }
}
