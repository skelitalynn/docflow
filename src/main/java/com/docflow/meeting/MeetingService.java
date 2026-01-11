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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

//屏幕共享
@Service
public class MeetingService {
    private static final String PROVIDER_JITSI = "jitsi";

    private final MeetingRepository meetingRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AclService aclService;
    private final WebSocketNotifier notifier;
    private final AuditService auditService;
    private final String jitsiBaseUrl;

    public MeetingService(MeetingRepository meetingRepository,
                          DocumentRepository documentRepository,
                          UserRepository userRepository,
                          AclService aclService,
                          WebSocketNotifier notifier,
                          AuditService auditService,
                          @Value("${docflow.meeting.jitsiBaseUrl:https://meet.jit.si}") String jitsiBaseUrl) {
        this.meetingRepository = meetingRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aclService = aclService;
        this.notifier = notifier;
        this.auditService = auditService;
        this.jitsiBaseUrl = normalizeBaseUrl(jitsiBaseUrl);
    }

    //会议开启
    @Transactional
    public Meeting start(Long userId, Long docId, String title, String provider, String joinUrl, String ip) {
        Document document = documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        //防止重复
        if (meetingRepository.findFirstByDocumentIdAndStatusOrderByCreatedAtDesc(docId, MeetingStatus.ACTIVE).isPresent()) {
            throw new ConflictException("Meeting already active", Map.of("docId", docId));
        }

        //保证会议一定有合法主持人
        User host = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String resolvedTitle = title == null || title.isBlank() ? "Team meeting" : title.trim();
        String resolvedProvider = provider == null || provider.isBlank() ? PROVIDER_JITSI : provider.trim();
        String resolvedUrl = joinUrl == null || joinUrl.isBlank()
                ? buildJoinUrl(resolvedProvider, docId)
                : joinUrl.trim();
        Meeting meeting = Meeting.builder()
                .document(document)
                .host(host)
                .title(resolvedTitle)
                .provider(resolvedProvider)
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


    //结束会议
    @Transactional
    public Meeting end(Long userId, Long docId, Long meetingId, String ip) {
        //查找会议并校验归属，防止跨文档操作会议，防止 ID 猜测攻击
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting not found"));
        if (!meeting.getDocument().getId().equals(docId)) {
            throw new NotFoundException("Meeting not found");
        }
        aclService.requireRole(userId, docId, DocRole.EDITOR);
        if (meeting.getStatus() == MeetingStatus.ENDED) {
            return meeting;
        }
        //确保会议幂等性
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

    private String buildJoinUrl(String provider, Long docId) {
        if (PROVIDER_JITSI.equalsIgnoreCase(provider)) {
            return jitsiBaseUrl + "/" + buildRoomName(docId);
        }
        return "https://meeting.local/" + docId + "/" + UUID.randomUUID();
    }

    private String buildRoomName(Long docId) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return "docflow-" + docId + "-" + token;
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "https://meet.jit.si";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    //查询当前进行中的会议
    public Meeting getActive(Long userId, Long docId) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        return meetingRepository.findFirstByDocumentIdAndStatusOrderByCreatedAtDesc(docId, MeetingStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active meeting not found"));
    }

    //查询会议历史
    public Page<Meeting> list(Long userId, Long docId, Pageable pageable) {
        documentRepository.findByIdAndDeletedFalse(docId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        aclService.requireRole(userId, docId, DocRole.VIEWER);
        return meetingRepository.findByDocumentIdOrderByCreatedAtDesc(docId, pageable);
    }
}
