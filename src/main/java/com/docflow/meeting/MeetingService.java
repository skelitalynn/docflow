package com.docflow.meeting;

import com.docflow.acl.AclService;
import com.docflow.audit.AuditService;
import com.docflow.collaboration.WebSocketNotifier;
import com.docflow.common.ConflictException;
import com.docflow.common.DocRole;
import com.docflow.common.MeetingStatus;
import com.docflow.common.NotFoundException;
import com.docflow.document.Document;
import com.docflow.document.DocumentRepository;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AclService aclService;
    private final WebSocketNotifier notifier;
    private final AuditService auditService;

    public MeetingService(MeetingRepository meetingRepository,
                          DocumentRepository documentRepository,
                          UserRepository userRepository,
                          AclService aclService,
                          WebSocketNotifier notifier,
                          AuditService auditService) {
        this.meetingRepository = meetingRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aclService = aclService;
        this.notifier = notifier;
        this.auditService = auditService;
    }

    @Transactional
    public Meeting start(Long userId, Long docId, String title, String provider, String joinUrl, String ip) {
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        if (meetingRepository.findFirstByDocumentIdAndStatusOrderByCreatedAtDesc(docId, MeetingStatus.ACTIVE).isPresent()) {
            throw new ConflictException("Meeting already active", Map.of("docId", docId));
        }
        User host = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String resolvedTitle = title == null || title.isBlank() ? "Team meeting" : title.trim();
        String resolvedUrl = joinUrl == null || joinUrl.isBlank()
                ? "https://meeting.local/" + docId + "/" + UUID.randomUUID()
                : joinUrl.trim();
        Meeting meeting = Meeting.builder()
                .document(document)
                .host(host)
                .title(resolvedTitle)
                .provider(provider == null ? "custom" : provider.trim())
                .joinUrl(resolvedUrl)
                .status(MeetingStatus.ACTIVE)
                .startedAt(LocalDateTime.now())
                .build();
        Meeting saved = meetingRepository.save(meeting);
        auditService.record(host, "meeting_start", "meeting", saved.getId(), document, true, null, ip,
                Map.of("provider", meeting.getProvider()));

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("docId", docId);
        payload.put("hostId", host.getId());
        payload.put("title", saved.getTitle());
        payload.put("provider", saved.getProvider());
        payload.put("joinUrl", saved.getJoinUrl());
        payload.put("startedAt", saved.getStartedAt());
        notifier.broadcastToDoc(docId, "meeting.start", payload);
        return saved;
    }

    @Transactional
    public Meeting end(Long userId, Long docId, Long meetingId, String ip) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting not found"));
        if (!meeting.getDocument().getId().equals(docId)) {
            throw new NotFoundException("Meeting not found");
        }
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        if (meeting.getStatus() == MeetingStatus.ENDED) {
            return meeting;
        }
        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setEndedAt(LocalDateTime.now());
        Meeting saved = meetingRepository.save(meeting);
        User host = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        auditService.record(host, "meeting_end", "meeting", saved.getId(), meeting.getDocument(), true, null, ip, null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("docId", docId);
        payload.put("endedAt", saved.getEndedAt());
        notifier.broadcastToDoc(docId, "meeting.end", payload);
        return saved;
    }

    public Meeting getActive(Long userId, Long docId) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        return meetingRepository.findFirstByDocumentIdAndStatusOrderByCreatedAtDesc(docId, MeetingStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active meeting not found"));
    }

    public Page<Meeting> list(Long userId, Long docId, Pageable pageable) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        return meetingRepository.findByDocumentIdOrderByCreatedAtDesc(docId, pageable);
    }
}
