package com.docflow.acl;

import com.docflow.common.DocRole;
import com.docflow.security.SecurityUtils;
import com.docflow.document.DocumentRepository;
import com.docflow.common.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/docs")
public class AclController {
    private final AclService aclService;
    private final DocumentRepository documentRepository;

    public AclController(AclService aclService, DocumentRepository documentRepository) {
        this.aclService = aclService;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> list(@PathVariable("id") Long docId) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.ensureOwnerOrAdmin(SecurityUtils.getCurrentUserId(), docId);
        return aclService.listMembers(docId).stream()
                .map(member -> new MemberResponse(
                        member.getUser().getId(),
                        member.getUser().getNickname(),
                        member.getRole()))
                .toList();
    }

    @PutMapping("/{id}/members")
    public MessageResponse update(@PathVariable("id") Long docId,
                                  @Valid @RequestBody AclUpdateRequest request,
                                  HttpServletRequest httpRequest) {
        aclService.upsertMember(SecurityUtils.getCurrentUserId(),
                docId,
                request.userId(),
                request.role(),
                request.remove(),
                clientIp(httpRequest));
        return new MessageResponse("ok");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record AclUpdateRequest(@NotNull Long userId, DocRole role, boolean remove) {
    }

    public record MemberResponse(Long userId, String nickname, DocRole role) {
    }

    public record MessageResponse(String message) {
    }
}
