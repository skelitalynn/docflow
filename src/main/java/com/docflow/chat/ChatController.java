package com.docflow.chat;

import com.docflow.common.PageResponse;
import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    //发送聊天信息
    @PostMapping("/docs/{id}/chat/messages")
    public ChatMessageResponse send(@PathVariable("id") Long docId,
                                    @Valid @RequestBody ChatSendRequest request,
                                    HttpServletRequest httpRequest) {
        //知道消息信息
        ChatMessage message = chatService.send(SecurityUtils.getCurrentUserId(),
                docId,
                request.content(),
                clientIp(httpRequest));
        return toResponse(message);
    }

    //查询聊天记录
    @GetMapping("/docs/{id}/chat/messages")
    public PageResponse<ChatMessageResponse> list(@PathVariable("id") Long docId,
                                                //分页设计，防止大结果集
                                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                                  @RequestParam(value = "size", defaultValue = "20") int size) {
        //前端请求历史聊天                                            
        Page<ChatMessage> messages = chatService.list(SecurityUtils.getCurrentUserId(),
                docId,
                PageRequest.of(page, size));

        //后端返回当前页的消息和页码信息
        List<ChatMessageResponse> items = messages.map(this::toResponse).getContent();
        //API封装统一
        return new PageResponse<>(items, messages.getNumber(), messages.getSize(),
                messages.getTotalElements(), messages.getTotalPages());
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getDocument().getId(),
                message.getSender().getId(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record ChatSendRequest(@NotBlank @Size(max = 2000) String content) {
    }

    public record ChatMessageResponse(Long id,
                                      Long docId,
                                      Long senderId,
                                      String content,
                                      LocalDateTime createdAt) {
    }
}
