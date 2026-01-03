package com.docflow.document;

import com.docflow.acl.DocMember;
import com.docflow.acl.DocMemberRepository;
import com.docflow.acl.AclService;
import com.docflow.audit.AuditService;
import com.docflow.collaboration.WebSocketNotifier;
import com.docflow.common.BadRequestException;
import com.docflow.common.ConflictException;
import com.docflow.common.DocRole;
import com.docflow.common.NotFoundException;
import com.docflow.folder.Folder;
import com.docflow.folder.FolderRepository;
import com.docflow.tag.DocumentTagService;
import com.docflow.template.DocumentTemplate;
import com.docflow.template.DocumentTemplateRepository;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final DocMemberRepository docMemberRepository;
    private final AclService aclService;
    private final AuditService auditService;
    private final WebSocketNotifier notifier;
    private final DocumentTemplateRepository templateRepository;
    private final DocumentTagService documentTagService;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           FolderRepository folderRepository,
                           DocMemberRepository docMemberRepository,
                           AclService aclService,
                           AuditService auditService,
                           WebSocketNotifier notifier,
                           DocumentTemplateRepository templateRepository,
                           DocumentTagService documentTagService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
        this.docMemberRepository = docMemberRepository;
        this.aclService = aclService;
        this.auditService = auditService;
        this.notifier = notifier;
        this.templateRepository = templateRepository;
        this.documentTagService = documentTagService;
    }

    @Transactional
    public Document create(Long userId,
                           String title,
                           String content,
                           Long folderId,
                           Long templateId,
                           DocFormat format,
                           java.util.List<Long> tagIds,
                           String ip) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndOwnerIdAndDeletedFalse(folderId, userId)
                    .orElseThrow(() -> new BadRequestException("Folder not found"));
        }
        DocumentTemplate template = null;
        if (templateId != null) {
            template = templateRepository.findAccessibleById(templateId, userId)
                    .orElseThrow(() -> new BadRequestException("Template not accessible"));
        }
        String resolvedTitle = title;
        String resolvedContent = content;
        DocFormat resolvedFormat = format;
        if (template != null) {
            if (resolvedTitle == null || resolvedTitle.isBlank()) {
                resolvedTitle = template.getTitle();
            }
            if (resolvedContent == null) {
                resolvedContent = template.getContent();
            }
            if (resolvedFormat == null) {
                resolvedFormat = template.getContentFormat();
            }
        }
        String safeTitle = (resolvedTitle == null || resolvedTitle.isBlank()) ? "Untitled" : resolvedTitle;
        DocFormat safeFormat = resolvedFormat == null ? DocFormat.RICH_TEXT : resolvedFormat;
        Document document = Document.builder()
                .title(safeTitle)
                .content(resolvedContent == null ? "" : resolvedContent)
                .contentFormat(safeFormat)
                .creator(creator)
                .owner(creator)
                .folder(folder)
                .version(1)
                .deleted(false)
                .build();
        Document saved = documentRepository.save(document);
        DocMember owner = DocMember.builder()
                .document(saved)
                .user(creator)
                .role(DocRole.OWNER)
                .grantedBy(creator)
                .build();
        docMemberRepository.save(owner);
        auditService.record(creator, "doc_create", "doc", saved.getId(), saved, true, null, ip, null);
        if (tagIds != null && !tagIds.isEmpty()) {
            documentTagService.setTags(saved.getId(), userId, tagIds, ip);
        }
        return saved;
    }

    public DocumentWithRole getDocument(Long userId, Long docId) {
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        DocRole role = aclService.requireRole(userId, docId, DocRole.VIEWER);
        return new DocumentWithRole(document, role);
    }

    @Transactional
    public Document updateContent(Long userId,
                                  Long docId,
                                  String content,
                                  int baseVersion,
                                  DocFormat format,
                                  String ip) {
        return saveContent(userId, docId, content, baseVersion, format, ip, "doc_save");
    }

    @Transactional
    public Document autoSave(Long userId,
                             Long docId,
                             String content,
                             int baseVersion,
                             DocFormat format,
                             String ip) {
        return saveContent(userId, docId, content, baseVersion, format, ip, "doc_autosave");
    }

    @Transactional
    public Document rename(Long userId, Long docId, String title, String ip) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Title is required");
        }
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        document.setTitle(title);
        Document saved = documentRepository.save(document);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        auditService.record(actor, "doc_rename", "doc", docId, document, true, null, ip,
                Map.of("title", title));
        return saved;
    }

    @Transactional
    public void delete(Long userId, Long docId, String ip) {
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.OWNER);
        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        auditService.record(actor, "doc_delete", "doc", docId, document, true, null, ip, null);
    }

    public Page<Document> list(Long userId, Long folderId, Long tagId, Pageable pageable) {
        return documentRepository.findAccessibleDocuments(userId, folderId, tagId, pageable);
    }

    public Page<Document> search(Long userId,
                                 String keyword,
                                 Long authorId,
                                 java.time.LocalDateTime from,
                                 java.time.LocalDateTime to,
                                 Long tagId,
                                 Pageable pageable) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword;
        return documentRepository.searchAccessibleDocuments(userId, trimmed, authorId, from, to, tagId, pageable);
    }

    private Document saveContent(Long userId,
                                 Long docId,
                                 String content,
                                 int baseVersion,
                                 DocFormat format,
                                 String ip,
                                 String action) {
        if (content == null) {
            throw new BadRequestException("Content is required");
        }
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        DocFormat currentFormat = document.getContentFormat() == null ? DocFormat.RICH_TEXT : document.getContentFormat();
        DocFormat resolvedFormat = format == null ? currentFormat : format;
        int updated = documentRepository.updateContentIfVersionMatches(docId, content, baseVersion, resolvedFormat);
        if (updated == 0) {
            Document current = documentRepository.findByIdAndDeletedFalse(docId)
                    .orElseThrow(() -> new NotFoundException("Document not found"));
            if (current.getVersion() != baseVersion) {
                throw new ConflictException("Version conflict",
                        Map.of("currentVersion", current.getVersion(), "updatedAt", current.getUpdatedAt()));
            }
        }
        Document saved = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        auditService.record(actor, action, "doc", docId, document, true, null, ip,
                Map.of("baseVersion", baseVersion, "newVersion", saved.getVersion()));
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("docId", saved.getId());
        payload.put("actorId", actor.getId());
        payload.put("content", saved.getContent());
        payload.put("format", saved.getContentFormat() != null ? saved.getContentFormat().name() : DocFormat.RICH_TEXT.name());
        payload.put("version", saved.getVersion());
        payload.put("updatedAt", saved.getUpdatedAt());
        notifier.broadcastToDoc(saved.getId(), "doc.sync", payload);
        return saved;
    }

    public record DocumentWithRole(Document document, DocRole role) {
    }
}
