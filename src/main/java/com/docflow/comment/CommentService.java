package com.docflow.comment;

import com.docflow.acl.AclService;
import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.CommentStatus;
import com.docflow.common.DocRole;
import com.docflow.common.NotFoundException;
import com.docflow.document.Document;
import com.docflow.document.DocumentRepository;
import com.docflow.notification.NotificationService;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final CommentMentionRepository mentionRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AclService aclService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public CommentService(CommentRepository commentRepository,
                          CommentMentionRepository mentionRepository,
                          DocumentRepository documentRepository,
                          UserRepository userRepository,
                          AclService aclService,
                          NotificationService notificationService,
                          AuditService auditService) {
        this.commentRepository = commentRepository;
        this.mentionRepository = mentionRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aclService = aclService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public Comment createComment(Long userId,
                                 Long docId,
                                 String blockId,
                                 Integer paragraphIndex,
                                 String content,
                                 List<Long> mentions,
                                 String ip) {
        if ((blockId == null || blockId.isBlank()) && paragraphIndex == null) {
            throw new BadRequestException("Anchor is required");
        }
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Comment comment = Comment.builder()
                .document(document)
                .author(author)
                .blockId(blockId)
                .paragraphIndex(paragraphIndex)
                .content(content)
                .status(CommentStatus.ACTIVE)
                .build();
        Comment saved = commentRepository.save(comment);
        saved.setThreadId(saved.getId());
        commentRepository.save(saved);
        processMentions(author, document, saved, mentions);
        auditService.record(author, "comment_create", "comment", saved.getId(), document, true, null, ip, null);
        return saved;
    }

    @Transactional
    public Comment reply(Long userId,
                         Long commentId,
                         String content,
                         List<Long> mentions,
                         String ip) {
        Comment parent = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        Document document = parent.getDocument();
        aclService.requireRole(userId, document.getId(), DocRole.VIEWER);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Comment reply = Comment.builder()
                .document(document)
                .author(author)
                .parent(parent)
                .threadId(parent.getThreadId())
                .content(content)
                .status(CommentStatus.ACTIVE)
                .build();
        Comment saved = commentRepository.save(reply);
        processMentions(author, document, saved, mentions);
        notificationService.notifyCommentReply(parent.getAuthor(), document, saved, "New reply");
        auditService.record(author, "comment_reply", "comment", saved.getId(), document, true, null, ip, Map.of());
        return saved;
    }

    public List<Comment> listByDocument(Long docId, int page, int size, Long userId) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        return commentRepository.findByDocumentIdOrderByCreatedAtAsc(docId, org.springframework.data.domain.PageRequest.of(page, size))
                .getContent();
    }

    private void processMentions(User author,
                                 Document document,
                                 Comment comment,
                                 List<Long> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return;
        }
        List<CommentMention> toSave = new ArrayList<>();
        for (Long userId : mentions.stream().distinct().toList()) {
            userRepository.findById(userId).ifPresent(target -> {
                CommentMention mention = CommentMention.builder()
                        .comment(comment)
                        .mentionedUser(target)
                        .mentionedBy(author)
                        .build();
                toSave.add(mention);
                notificationService.notifyMention(target, document, comment, "You were mentioned");
            });
        }
        if (!toSave.isEmpty()) {
            mentionRepository.saveAll(toSave);
        }
    }
}
