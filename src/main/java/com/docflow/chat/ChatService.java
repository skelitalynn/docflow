package com.docflow.chat;

import com.docflow.acl.AclService;
import com.docflow.audit.AuditService;
import com.docflow.collaboration.WebSocketNotifier;
import com.docflow.common.BadRequestException;
import com.docflow.common.DocRole;
import com.docflow.common.NotFoundException;
import com.docflow.document.Document;
import com.docflow.document.DocumentRepository;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class ChatService {
    private final ChatMessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AclService aclService;
    private final WebSocketNotifier notifier;
    private final AuditService auditService;

    public ChatService(ChatMessageRepository messageRepository,
                       DocumentRepository documentRepository,
                       UserRepository userRepository,
                       AclService aclService,
                       WebSocketNotifier notifier,
                       AuditService auditService) {
        this.messageRepository = messageRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aclService = aclService;
        this.notifier = notifier;
        this.auditService = auditService;
    }

    //发送聊天消息
    @Transactional
    public ChatMessage send(Long userId, Long docId, String content, String ip) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Message content required");
        }
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        //构建聊天实体
        ChatMessage message = ChatMessage.builder()
                .document(document)
                .sender(sender)
                .content(content.trim())
                .build();
        //聊天内容入库
        ChatMessage saved = messageRepository.save(message);

        //构建websocket时间payload
        //不用entity，避免依赖后端领域模型，降低耦合
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("docId", docId);
        payload.put("senderId", sender.getId());
        payload.put("content", saved.getContent());
        payload.put("createdAt", saved.getCreatedAt());
        notifier.broadcastToDoc(docId, "chat.message", payload);

        auditService.record(sender, "chat_send", "chat", saved.getId(), document, true, null, ip, null);
        return saved;
    }

    //分页查询聊天记录
    public Page<ChatMessage> list(Long userId, Long docId, Pageable pageable) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        return messageRepository.findByDocumentIdOrderByCreatedAtDesc(docId, pageable);
    }
}
