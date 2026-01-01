package com.docflow.comment;

import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    public record CommentResponse(Long id,
                                  Long docId,
                                  Long threadId,
                                  Long parentId,
                                  Long authorId,
                                  String blockId,
                                  Integer paragraphIndex,
                                  String content,
                                  LocalDateTime createdAt) {
    }
}
