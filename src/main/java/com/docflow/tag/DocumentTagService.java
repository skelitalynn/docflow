package com.docflow.tag;

import com.docflow.acl.AclService;
import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.NotFoundException;
import com.docflow.document.Document;
import com.docflow.document.DocumentRepository;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentTagService {
    private final DocTagRepository docTagRepository;
    private final TagRepository tagRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AclService aclService;
    private final AuditService auditService;

    public DocumentTagService(DocTagRepository docTagRepository,
                              TagRepository tagRepository,
                              DocumentRepository documentRepository,
                              UserRepository userRepository,
                              AclService aclService,
                              AuditService auditService) {
        this.docTagRepository = docTagRepository;
        this.tagRepository = tagRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aclService = aclService;
        this.auditService = auditService;
    }

    public List<Tag> listTags(Long docId, Long userId) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, com.docflow.common.DocRole.VIEWER);
        return docTagRepository.findByDocumentId(docId).stream()
                .map(DocTag::getTag)
                .collect(Collectors.toList());
    }

    @Transactional
    public void setTags(Long docId, Long userId, List<Long> tagIds, String ip) {
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.ensureOwnerOrAdmin(userId, docId);
        User operator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<Long> ids = tagIds == null ? List.of() : tagIds.stream().distinct().toList();
        List<Tag> tags = ids.isEmpty() ? List.of() : tagRepository.findAllById(ids);
        if (tags.size() != ids.size()) {
            throw new BadRequestException("Tag not found");
        }
        for (Tag tag : tags) {
            if (!tag.getOwner().getId().equals(userId)) {
                throw new BadRequestException("Tag not owned by user");
            }
        }

        docTagRepository.deleteByDocumentId(docId);
        for (Tag tag : tags) {
            DocTag docTag = DocTag.builder()
                    .document(document)
                    .tag(tag)
                    .build();
            docTagRepository.save(docTag);
        }
        auditService.record(operator, "doc_tag_update", "doc", docId, document, true, null, ip,
                Map.of("tagIds", ids));
    }
}
