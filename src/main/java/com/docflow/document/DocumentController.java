package com.docflow.document;

import com.docflow.acl.DocMemberRepository;
import com.docflow.common.DocRole;
import com.docflow.common.PageResponse;
import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

// Document APIs: create/edit/autosave/versioning/list/search with ACL checks in service.
@RestController
@RequestMapping
public class DocumentController {
    private final DocumentService documentService;
    private final DocMemberRepository docMemberRepository;

    public DocumentController(DocumentService documentService, DocMemberRepository docMemberRepository) {
        this.documentService = documentService;
        this.docMemberRepository = docMemberRepository;
    }

    // Create a document, optionally using folder/template/tags.
    @PostMapping("/docs")
    public DocumentResponse create(@Valid @RequestBody CreateDocumentRequest request, HttpServletRequest httpRequest) {
        Document document = documentService.create(SecurityUtils.getCurrentUserId(),
                request.title(),
                request.content(),
                request.folderId(),
                request.templateId(),
                request.format(),
                request.tagIds(),
                clientIp(httpRequest));
        return toResponse(document, DocRole.OWNER);
    }

    // Fetch a document with current role information.
    @GetMapping("/docs/{id}")
    public DocumentResponse get(@PathVariable("id") Long id) {
        DocumentService.DocumentWithRole result = documentService.getDocument(SecurityUtils.getCurrentUserId(), id);
        return toResponse(result.document(), result.role());
    }

    // Manual save (increments version, may conflict on baseVersion).
    @PutMapping("/docs/{id}")
    public DocumentResponse updateContent(@PathVariable("id") Long id,
                                          @Valid @RequestBody UpdateDocumentRequest request,
                                          HttpServletRequest httpRequest) {
        Document document = documentService.updateContent(SecurityUtils.getCurrentUserId(),
                id,
                request.content(),
                request.baseVersion(),
                request.format(),
                clientIp(httpRequest));
        DocRole role = docMemberRepository.findByDocumentIdAndUserId(id, SecurityUtils.getCurrentUserId())
                .map(member -> member.getRole()).orElse(DocRole.VIEWER);
        return toResponse(document, role);
    }

    // Autosave (same conflict rules, no edit notification).
    @PutMapping("/docs/{id}/autosave")
    public DocumentResponse autoSave(@PathVariable("id") Long id,
                                     @Valid @RequestBody UpdateDocumentRequest request,
                                     HttpServletRequest httpRequest) {
        Document document = documentService.autoSave(SecurityUtils.getCurrentUserId(),
                id,
                request.content(),
                request.baseVersion(),
                request.format(),
                clientIp(httpRequest));
        DocRole role = docMemberRepository.findByDocumentIdAndUserId(id, SecurityUtils.getCurrentUserId())
                .map(member -> member.getRole()).orElse(DocRole.VIEWER);
        return toResponse(document, role);
    }

    // Rename title (requires EDITOR or above).
    @PutMapping("/docs/{id}/title")
    public DocumentResponse rename(@PathVariable("id") Long id,
                                   @Valid @RequestBody RenameDocumentRequest request,
                                   HttpServletRequest httpRequest) {
        Document document = documentService.rename(SecurityUtils.getCurrentUserId(),
                id, request.title(), clientIp(httpRequest));
        DocRole role = docMemberRepository.findByDocumentIdAndUserId(id, SecurityUtils.getCurrentUserId())
                .map(member -> member.getRole()).orElse(DocRole.VIEWER);
        return toResponse(document, role);
    }

    // Soft delete (OWNER only).
    @DeleteMapping("/docs/{id}")
    public MessageResponse delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        documentService.delete(SecurityUtils.getCurrentUserId(), id, clientIp(httpRequest));
        return new MessageResponse("ok");
    }

    // List accessible documents with folder/tag filters and sorting.
    @GetMapping("/docs")
    public PageResponse<DocumentSummary> list(@RequestParam(value = "folderId", required = false) Long folderId,
                                              @RequestParam(value = "tagId", required = false) Long tagId,
                                              @RequestParam(value = "page", defaultValue = "0") int page,
                                              @RequestParam(value = "size", defaultValue = "20") int size,
                                              @RequestParam(value = "sortBy", defaultValue = "updatedAt") String sortBy,
                                              @RequestParam(value = "order", defaultValue = "desc") String order) {
        Page<Document> docs = documentService.list(SecurityUtils.getCurrentUserId(),
                folderId,
                tagId,
                pageRequest(page, size, sortBy, order));
        List<DocumentSummary> summaries = docs.map(doc -> new DocumentSummary(
                doc.getId(),
                doc.getTitle(),
                doc.getFolder() != null ? doc.getFolder().getId() : null,
                safeFormat(doc),
                doc.getUpdatedAt()
        )).getContent();
        return new PageResponse<>(summaries, docs.getNumber(), docs.getSize(), docs.getTotalElements(), docs.getTotalPages());
    }

    // Search across accessible docs (keyword/author/time/tag + sort).
    @GetMapping("/search")
    public PageResponse<DocumentSummary> search(@RequestParam(value = "q", required = false) String keyword,
                                                @RequestParam(value = "authorId", required = false) Long authorId,
                                                @RequestParam(value = "from", required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                                @RequestParam(value = "to", required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                                @RequestParam(value = "tagId", required = false) Long tagId,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "size", defaultValue = "20") int size,
                                                @RequestParam(value = "sortBy", defaultValue = "updatedAt") String sortBy,
                                                @RequestParam(value = "order", defaultValue = "desc") String order) {
        Page<Document> docs = documentService.search(SecurityUtils.getCurrentUserId(),
                keyword,
                authorId,
                from,
                to,
                tagId,
                pageRequestForSearch(page, size, sortBy, order));
        List<DocumentSummary> summaries = docs.map(doc -> new DocumentSummary(
                doc.getId(),
                doc.getTitle(),
                doc.getFolder() != null ? doc.getFolder().getId() : null,
                safeFormat(doc),
                doc.getUpdatedAt()
        )).getContent();
        return new PageResponse<>(summaries, docs.getNumber(), docs.getSize(), docs.getTotalElements(), docs.getTotalPages());
    }

    // List version history (all/manual/autosave).
    @GetMapping("/docs/{id}/versions")
    public PageResponse<VersionSummary> listVersions(@PathVariable("id") Long docId,
                                                     @RequestParam(value = "type", defaultValue = "all") String type,
                                                     @RequestParam(value = "page", defaultValue = "0") int page,
                                                     @RequestParam(value = "size", defaultValue = "20") int size) {
        Boolean autosave = null;
        if ("autosave".equalsIgnoreCase(type)) {
            autosave = true;
        } else if ("manual".equalsIgnoreCase(type)) {
            autosave = false;
        }
        Page<DocumentVersion> versions = documentService.listVersions(SecurityUtils.getCurrentUserId(),
                docId,
                autosave,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<VersionSummary> items = versions.map(version -> new VersionSummary(
                version.getId(),
                version.getVersionNumber(),
                version.getContentFormat(),
                version.isAutosave(),
                version.getActor().getId(),
                version.getCreatedAt()
        )).getContent();
        return new PageResponse<>(items, versions.getNumber(), versions.getSize(),
                versions.getTotalElements(), versions.getTotalPages());
    }

    // Get a single version detail (content snapshot).
    @GetMapping("/docs/{id}/versions/{versionId}")
    public VersionDetail getVersion(@PathVariable("id") Long docId,
                                    @PathVariable("versionId") Long versionId) {
        DocumentVersion version = documentService.getVersion(SecurityUtils.getCurrentUserId(), docId, versionId);
        return new VersionDetail(version.getId(),
                version.getVersionNumber(),
                version.getContent(),
                version.getContentFormat(),
                version.isAutosave(),
                version.getActor().getId(),
                version.getCreatedAt());
    }

    // Restore a version (also goes through version conflict check).
    @PostMapping("/docs/{id}/versions/{versionId}/restore")
    public DocumentResponse restoreVersion(@PathVariable("id") Long docId,
                                           @PathVariable("versionId") Long versionId,
                                           @Valid @RequestBody RestoreRequest request,
                                           HttpServletRequest httpRequest) {
        Document restored = documentService.restoreVersion(SecurityUtils.getCurrentUserId(),
                docId,
                versionId,
                request.baseVersion(),
                clientIp(httpRequest));
        DocRole role = docMemberRepository.findByDocumentIdAndUserId(docId, SecurityUtils.getCurrentUserId())
                .map(member -> member.getRole()).orElse(DocRole.VIEWER);
        return toResponse(restored, role);
    }

    private DocumentResponse toResponse(Document document, DocRole role) {
        return new DocumentResponse(document.getId(),
                document.getTitle(),
                document.getContent(),
                safeFormat(document),
                document.getVersion(),
                document.getUpdatedAt(),
                role);
    }

    private PageRequest pageRequest(int page, int size, String sortBy, String order) {
        String property = "updatedAt";
        if ("createdAt".equalsIgnoreCase(sortBy)) {
            property = "createdAt";
        } else if ("title".equalsIgnoreCase(sortBy)) {
            property = "title";
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private PageRequest pageRequestForSearch(int page, int size, String sortBy, String order) {
        String property = "updated_at";
        if ("createdAt".equalsIgnoreCase(sortBy)) {
            property = "created_at";
        } else if ("title".equalsIgnoreCase(sortBy)) {
            property = "title";
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private DocFormat safeFormat(Document document) {
        return document.getContentFormat() == null ? DocFormat.RICH_TEXT : document.getContentFormat();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record CreateDocumentRequest(String title,
                                        String content,
                                        Long folderId,
                                        Long templateId,
                                        DocFormat format,
                                        List<Long> tagIds) {
    }

    public record UpdateDocumentRequest(@NotNull String content,
                                        @NotNull Integer baseVersion,
                                        DocFormat format) {
    }

    public record RenameDocumentRequest(@NotBlank String title) {
    }

    public record DocumentResponse(Long id,
                                   String title,
                                   String content,
                                   DocFormat format,
                                   int version,
                                   LocalDateTime updatedAt,
                                   DocRole role) {
    }

    public record DocumentSummary(Long id,
                                  String title,
                                  Long folderId,
                                  DocFormat format,
                                  LocalDateTime updatedAt) {
    }

    public record VersionSummary(Long id,
                                 int versionNumber,
                                 DocFormat format,
                                 boolean autosave,
                                 Long actorId,
                                 LocalDateTime createdAt) {
    }

    public record VersionDetail(Long id,
                                int versionNumber,
                                String content,
                                DocFormat format,
                                boolean autosave,
                                Long actorId,
                                LocalDateTime createdAt) {
    }

    public record RestoreRequest(@NotNull Integer baseVersion) {
    }

    public record MessageResponse(String message) {
    }
}
