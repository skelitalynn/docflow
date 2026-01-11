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
        // 行内批注必须带锚点（块 ID 或段落序号）。
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
                .status(CommentStatus.OPEN)
                .build();
        Comment saved = commentRepository.save(comment);
        // 首条评论作为线程根，threadId 指向自身。
        saved.setThreadId(saved.getId());
        commentRepository.save(saved);
        //处理@人
        processMentions(author, document, saved, mentions);
        if (!author.getId().equals(document.getOwner().getId())) {
            notificationService.notifyComment(document.getOwner(), document, saved, "New comment");
        }
        auditService.record(author, "comment_create", "comment", saved.getId(), document, true, null, ip, null);
        return saved;
    }

    //回复评论
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
                // 回复继承父评论的 threadId。
                .threadId(parent.getThreadId())
                .content(content)
                .status(CommentStatus.OPEN)
                .build();
        Comment saved = commentRepository.save(reply);
        processMentions(author, document, saved, mentions);
        //@ 人 + 通知被回复的人
        notificationService.notifyCommentReply(parent.getAuthor(), document, saved, "New reply");
        auditService.record(author, "comment_reply", "comment", saved.getId(), document, true, null, ip, Map.of());
        return saved;
    }

    @Transactional
    public Comment updateStatus(Long userId,
                                Long commentId,
                                CommentStatus status,
                                String ip) {
        if (status == null) {
            throw new BadRequestException("Status is required");
        }
        if (status == CommentStatus.DELETED) {
            throw new BadRequestException("Unsupported status");
        }
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        Document document = comment.getDocument();
        User operator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        // 作者可直接改状态，其他人需要编辑者权限。
        if (!comment.getAuthor().getId().equals(userId)) {
            aclService.requireRole(userId, document.getId(), DocRole.EDITOR);
        }
        if (comment.getStatus() == status) {
            // 状态未变化则直接返回。
            return comment;
        }
        comment.setStatus(status);
        Comment saved = commentRepository.save(comment);
        auditService.record(operator, "comment_status", "comment", saved.getId(), document, true, null, ip,
                Map.of("status", status.name()));
        // 仅在他人操作时通知作者。
        if (!operator.getId().equals(comment.getAuthor().getId())) {
            String message = status == CommentStatus.RESOLVED ? "Comment resolved" : "Comment reopened";
            notificationService.notifyCommentStatus(comment.getAuthor(), document, saved, message);
        }
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
        // 去重提及用户并发送通知。
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
