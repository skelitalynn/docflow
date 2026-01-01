package com.docflow.acl;

import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.ConflictException;
import com.docflow.common.DocRole;
import com.docflow.common.NotFoundException;
import com.docflow.common.SystemRole;
import com.docflow.document.Document;
import com.docflow.document.DocumentRepository;
import com.docflow.notification.NotificationService;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AclService {
    private final DocMemberRepository docMemberRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public AclService(DocMemberRepository docMemberRepository,
                      DocumentRepository documentRepository,
                      UserRepository userRepository,
                      NotificationService notificationService,
                      AuditService auditService) {
        this.docMemberRepository = docMemberRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public DocRole requireRole(Long userId, Long docId, DocRole required) {
        DocRole role = docMemberRepository.findByDocumentIdAndUserId(docId, userId)
                .map(DocMember::getRole)
                .orElseThrow(() -> new AccessDeniedException("No document access"));
        if (!role.atLeast(required)) {
            throw new AccessDeniedException("No document access");
        }
        return role;
    }

    public void ensureOwnerOrAdmin(Long userId, Long docId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getSystemRole() == SystemRole.ADMIN) {
            return;
        }
        requireRole(userId, docId, DocRole.OWNER);
    }

    public List<DocMember> listMembers(Long docId) {
        return docMemberRepository.findWithUserByDocumentId(docId);
    }

    @Transactional
    public void upsertMember(Long operatorId,
                             Long docId,
                             Long targetUserId,
                             DocRole role,
                             boolean remove,
                             String ip) {
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BadRequestException("Target user not found"));
        ensureOwnerOrAdmin(operatorId, docId);

        DocMember existing = docMemberRepository.findByDocumentIdAndUserId(docId, targetUserId).orElse(null);
        if (remove) {
            if (existing != null) {
                if (existing.getRole() == DocRole.OWNER && docMemberRepository.countByDocumentIdAndRole(docId, DocRole.OWNER) <= 1) {
                    throw new ConflictException("Cannot remove last owner", Map.of("docId", docId));
                }
                docMemberRepository.delete(existing);
                auditService.record(operator, "acl_remove", "acl", existing.getId(), document, true, null, ip,
                        Map.of("userId", targetUserId, "role", existing.getRole().name()));
                notificationService.notifyShare(target, document, "Permission revoked");
            }
            return;
        }
        if (role == null) {
            throw new BadRequestException("Role is required");
        }
        if (existing != null && existing.getRole() == DocRole.OWNER && role != DocRole.OWNER) {
            if (docMemberRepository.countByDocumentIdAndRole(docId, DocRole.OWNER) <= 1) {
                throw new ConflictException("Cannot remove last owner", Map.of("docId", docId));
            }
        }
        DocRole before = existing == null ? null : existing.getRole();
        DocMember member = existing == null ? new DocMember() : existing;
        member.setDocument(document);
        member.setUser(target);
        member.setRole(role);
        member.setGrantedBy(operator);
        docMemberRepository.save(member);
        Map<String, Object> meta = new HashMap<>();
        meta.put("userId", targetUserId);
        meta.put("before", before == null ? null : before.name());
        meta.put("after", role.name());
        auditService.record(operator, "acl_update", "acl", member.getId(), document, true, null, ip, meta);
        notificationService.notifyShare(target, document, "Permission updated");
    }
}
