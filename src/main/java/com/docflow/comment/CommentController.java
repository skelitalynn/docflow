package com.docflow.comment;

import com.docflow.common.CommentStatus;
import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/docs/{id}/comments")
    public CommentResponse create(@PathVariable("id") Long docId,
                                  @Valid @RequestBody CreateCommentRequest request,
                                  HttpServletRequest httpRequest) {
        Comment comment = commentService.createComment(SecurityUtils.getCurrentUserId(),
                docId,
                request.blockId(),
                request.paragraphIndex(),
                request.content(),
                request.mentions(),
                clientIp(httpRequest));
        return toResponse(comment);
    }

    @PostMapping("/comments/{id}/replies")
    public CommentResponse reply(@PathVariable("id") Long commentId,
                                 @Valid @RequestBody ReplyRequest request,
                                 HttpServletRequest httpRequest) {
        Comment reply = commentService.reply(SecurityUtils.getCurrentUserId(),
                commentId,
                request.content(),
                request.mentions(),
                clientIp(httpRequest));
        return toResponse(reply);
    }

    @PutMapping("/comments/{id}/status")
    // 批注状态更新（OPEN/RESOLVED）。
    public CommentResponse updateStatus(@PathVariable("id") Long commentId,
                                        @Valid @RequestBody UpdateStatusRequest request,
                                        HttpServletRequest httpRequest) {
        Comment updated = commentService.updateStatus(SecurityUtils.getCurrentUserId(),
                commentId,
                request.status(),
                clientIp(httpRequest));
        return toResponse(updated);
    }

    @GetMapping("/docs/{id}/comments")
    public List<CommentResponse> list(@PathVariable("id") Long docId,
                                      @RequestParam(value = "page", defaultValue = "0") int page,
                                      @RequestParam(value = "size", defaultValue = "50") int size) {
        return commentService.listByDocument(docId, page, size, SecurityUtils.getCurrentUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getDocument().getId(),
                comment.getThreadId(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getAuthor().getId(),
                comment.getBlockId(),
                comment.getParagraphIndex(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt()
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record CreateCommentRequest(String blockId,
                                       Integer paragraphIndex,
                                       @NotBlank String content,
                                       List<Long> mentions) {
    }

    public record ReplyRequest(@NotBlank String content,
                               List<Long> mentions) {
    }

    public record UpdateStatusRequest(@NotNull CommentStatus status) {
    }

    public record CommentResponse(Long id,
                                  Long docId,
                                  Long threadId,
                                  Long parentId,
                                  Long authorId,
                                  String blockId,
                                  Integer paragraphIndex,
                                  String content,
                                  CommentStatus status,
                                  LocalDateTime createdAt) {
    }
}
