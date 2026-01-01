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

@RestController
@RequestMapping
public class DocumentController {
    private final DocumentService documentService;
    private final DocMemberRepository docMemberRepository;

    public DocumentController(DocumentService documentService, DocMemberRepository docMemberRepository) {
        this.documentService = documentService;
        this.docMemberRepository = docMemberRepository;
    }

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

    @GetMapping("/docs/{id}")
    public DocumentResponse get(@PathVariable("id") Long id) {
        DocumentService.DocumentWithRole result = documentService.getDocument(SecurityUtils.getCurrentUserId(), id);
        return toResponse(result.document(), result.role());
    }

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

    @DeleteMapping("/docs/{id}")
    public MessageResponse delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        documentService.delete(SecurityUtils.getCurrentUserId(), id, clientIp(httpRequest));
        return new MessageResponse("ok");
    }

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

    public record MessageResponse(String message) {
    }
}
