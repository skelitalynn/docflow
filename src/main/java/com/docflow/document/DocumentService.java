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
import com.docflow.notification.NotificationService;
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
    private final NotificationService notificationService;
    private final DocumentTemplateRepository templateRepository;
    private final DocumentTagService documentTagService;
    private final DocumentVersionRepository versionRepository;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           FolderRepository folderRepository,
                           DocMemberRepository docMemberRepository,
                           AclService aclService,
                           AuditService auditService,
                           WebSocketNotifier notifier,
                           NotificationService notificationService,
                           DocumentTemplateRepository templateRepository,
                           DocumentTagService documentTagService,
                           DocumentVersionRepository versionRepository) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
        this.docMemberRepository = docMemberRepository;
        this.aclService = aclService;
        this.auditService = auditService;
        this.notifier = notifier;
        this.notificationService = notificationService;
        this.templateRepository = templateRepository;
        this.documentTagService = documentTagService;
        this.versionRepository = versionRepository;
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

        //找创建者
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Folder folder = null;
        //文件夹可选，但是要校验userId
        if (folderId != null) {
            folder = folderRepository.findByIdAndOwnerIdAndDeletedFalse(folderId, userId)
                    .orElseThrow(() -> new BadRequestException("Folder not found"));
        }
        //模版有权限才能用
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
        //真正创建Document
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
        //创建DocMember，把自己加入成员表并设置OWNER
        DocMember owner = DocMember.builder()
                .document(saved)
                .user(creator)
                .role(DocRole.OWNER)
                .grantedBy(creator)
                .build();
        docMemberRepository.save(owner);
        //记录审计+记录版本
        auditService.record(creator, "doc_create", "doc", saved.getId(), saved, true, null, ip, null);
        recordVersion(saved, creator, false);
        if (tagIds != null && !tagIds.isEmpty()) {
            //绑定标签
            documentTagService.setTags(saved.getId(), userId, tagIds, ip);
        }
        return saved;
    }

    //打开文档
    public DocumentWithRole getDocument(Long userId, Long docId) {
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        DocRole role = aclService.requireRole(userId, docId, DocRole.VIEWER);
        //返回文档+user权限
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
        //需要EDITOR权限修改标题
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        document.setTitle(title);
        Document saved = documentRepository.save(document);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        auditService.record(actor, "doc_rename", "doc", docId, document, true, null, ip,
                Map.of("title", title));
        return saved;
    }

    //删除文档：软删除
    @Transactional
    public void delete(Long userId, Long docId, String ip) {
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.OWNER);
        //只有OWNER能删除
        //打标记+时间
        //可以恢复/审计/避免误删 
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
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        //只返回有权限的文档
        return documentRepository.searchAccessibleDocumentsFullText(userId, trimmed, authorId, from, to, tagId, pageable);
    }

    //保存文档内容
    private Document saveContent(Long userId,
                                 Long docId,
                                 String content,
                                 int baseVersion,
                                 DocFormat format,
                                 String ip,
                                 String action) {

        //content不为空
        if (content == null) {
            throw new BadRequestException("Content is required");
        }
        //文档存在且未删除
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));

        //EDITOR权限
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        //格式
        DocFormat currentFormat = document.getContentFormat() == null ? DocFormat.RICH_TEXT : document.getContentFormat();
        DocFormat resolvedFormat = format == null ? currentFormat : format;
        //版本匹配更新
        //前端保存时带着baseVersion,乐观锁
        //只有当数据库的version与baseVersion相等才更新
        int updated = documentRepository.updateContentIfVersionMatches(docId, content, baseVersion, resolvedFormat);
        if (updated == 0) {
            Document current = documentRepository.findByIdAndDeletedFalse(docId)
                    .orElseThrow(() -> new NotFoundException("Document not found"));
            if (current.getVersion() != baseVersion) {
                throw new ConflictException("Version conflict",
                        Map.of("currentVersion", current.getVersion(), "updatedAt", current.getUpdatedAt()));
            }
        }
        //保存成功后：再检查一遍拿到最新文档（拿到version和updatedAt）
        Document saved = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        auditService.record(actor, action, "doc", docId, document, true, null, ip,
                Map.of("baseVersion", baseVersion, "newVersion", saved.getVersion()));
        recordVersion(saved, actor, "doc_autosave".equals(action));
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("docId", saved.getId());
        payload.put("actorId", actor.getId());
        payload.put("content", saved.getContent());
        payload.put("format", saved.getContentFormat() != null ? saved.getContentFormat().name() : DocFormat.RICH_TEXT.name());
        payload.put("version", saved.getVersion());
        payload.put("updatedAt", saved.getUpdatedAt());
        //WebSocket 同步给在线协作者（doc.sync）
        notifier.broadcastToDoc(saved.getId(), "doc.sync", payload);
        if ("doc_save".equals(action) || "doc_restore".equals(action)) {
            notifyDocEdited(actor, saved);
        }
        return saved;
    }

    private void notifyDocEdited(User actor, Document document) {
        for (DocMember member : docMemberRepository.findWithUserByDocumentId(document.getId())) {
            User target = member.getUser();
            if (target.getId().equals(actor.getId())) {
                continue;
            }
            notificationService.notifyDocumentEdited(target, document, "Document updated");
        }
    }

    
    //按是否 autosave 过滤版本列表（给“历史版本”页面用）
    public Page<DocumentVersion> listVersions(Long userId,
                                              Long docId,
                                              Boolean autosave,
                                              Pageable pageable) {
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        if (autosave == null) {
            return versionRepository.findByDocumentId(docId, pageable);
        }
        return versionRepository.findByDocumentIdAndAutosave(docId, autosave, pageable);
    }

    //拿某个版本（用于预览/恢复）
    public DocumentVersion getVersion(Long userId, Long docId, Long versionId) {
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        return versionRepository.findByIdAndDocumentId(versionId, docId)
                .orElseThrow(() -> new NotFoundException("Version not found"));
    }


    //取出历史版本内容，走一次 saveContent（action=doc_restore）
    @Transactional
    public Document restoreVersion(Long userId,
                                   Long docId,
                                   Long versionId,
                                   int baseVersion,
                                   String ip) {
        DocumentVersion version = getVersion(userId, docId, versionId);
        return saveContent(userId, docId, version.getContent(), baseVersion, version.getContentFormat(), ip, "doc_restore");
    }

    private void recordVersion(Document document, User actor, boolean autosave) {
        DocumentVersion version = DocumentVersion.builder()
                .document(document)
                .versionNumber(document.getVersion())
                .content(document.getContent())
                .contentFormat(document.getContentFormat())
                .autosave(autosave)
                .actor(actor)
                .build();
        versionRepository.save(version);
    }

    public record DocumentWithRole(Document document, DocRole role) {
    }
}
