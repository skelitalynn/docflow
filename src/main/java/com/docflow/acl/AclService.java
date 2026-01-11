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
// 文档级访问控制（ACL）：成员权限校验/授权/移除，并写审计与通知。
public class AclService {
    private final DocMemberRepository docMemberRepository;// 文档-成员-角色 关系表
    private final DocumentRepository documentRepository;// 文档是否存在
    private final UserRepository userRepository; // 人是否存在
    private final NotificationService notificationService;// 权限变更要通知
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

    //查看user是不是doc的成员
    public DocRole requireRole(Long userId, Long docId, DocRole required) {
        // 文档级权限校验，拒绝无权限访问。
        DocRole role = docMemberRepository.findByDocumentIdAndUserId(docId, userId)
                .map(DocMember::getRole)
                .orElseThrow(() -> new AccessDeniedException("No document access"));
        if (!role.atLeast(required)) {
            //判断权限是否足够
            throw new AccessDeniedException("No document access");
        }
        return role;
    }

    //谁可以改权限
    public void ensureOwnerOrAdmin(Long userId, Long docId) {
        // 仅 Owner 或 Admin 可修改 ACL。
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



    //加人 / 改权限 / 删人
    @Transactional
    public void upsertMember(Long operatorId,
                             Long docId,
                             Long targetUserId,
                             DocRole role,
                             boolean remove,
                             String ip) {
        // 更新/移除成员时需保证至少保留一个 Owner。
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BadRequestException("Target user not found"));
        ensureOwnerOrAdmin(operatorId, docId);

        //检查user是否在doc中
        DocMember existing = docMemberRepository.findByDocumentIdAndUserId(docId, targetUserId).orElse(null);
        if (remove) {
            if (existing != null) {
                //保证一个文档里至少有一个OWNER，防止文档变成“孤儿”文档
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
        //如果降级OWNER，还要保证有其他OWNER
        //孤儿文档问题
        if (existing != null && existing.getRole() == DocRole.OWNER && role != DocRole.OWNER) {
            if (docMemberRepository.countByDocumentIdAndRole(docId, DocRole.OWNER) <= 1) {
                throw new ConflictException("Cannot remove last owner", Map.of("docId", docId));
            }
        }
        DocRole before = existing == null ? null : existing.getRole();
        //真正入库
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
