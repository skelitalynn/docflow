package com.docflow.meeting;

import com.docflow.common.MeetingStatus;
import com.docflow.common.PageResponse;
import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
public class MeetingController {
    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping("/docs/{id}/meetings")
    public MeetingResponse start(@PathVariable("id") Long docId,
                                 @Valid @RequestBody MeetingCreateRequest request,
                                 HttpServletRequest httpRequest) {
        Meeting meeting = meetingService.start(SecurityUtils.getCurrentUserId(),
                docId,
                request.title(),
                request.provider(),
                request.joinUrl(),
                clientIp(httpRequest));
        return toResponse(meeting);
    }

    @PostMapping("/docs/{id}/meetings/{meetingId}/end")
    public MeetingResponse end(@PathVariable("id") Long docId,
                               @PathVariable("meetingId") Long meetingId,
                               HttpServletRequest httpRequest) {
        Meeting meeting = meetingService.end(SecurityUtils.getCurrentUserId(),
                docId,
                meetingId,
                clientIp(httpRequest));
        return toResponse(meeting);
    }

    @GetMapping("/docs/{id}/meetings/active")
    public MeetingResponse active(@PathVariable("id") Long docId) {
        Meeting meeting = meetingService.getActive(SecurityUtils.getCurrentUserId(), docId);
        return toResponse(meeting);
    }

    @GetMapping("/docs/{id}/meetings")
    public PageResponse<MeetingResponse> list(@PathVariable("id") Long docId,
                                              @RequestParam(value = "page", defaultValue = "0") int page,
                                              @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Meeting> meetings = meetingService.list(SecurityUtils.getCurrentUserId(),
                docId,
                PageRequest.of(page, size));
        List<MeetingResponse> items = meetings.map(this::toResponse).getContent();
        return new PageResponse<>(items, meetings.getNumber(), meetings.getSize(),
                meetings.getTotalElements(), meetings.getTotalPages());
    }

    private MeetingResponse toResponse(Meeting meeting) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getDocument().getId(),
                meeting.getHost().getId(),
                meeting.getTitle(),
                meeting.getProvider(),
                meeting.getJoinUrl(),
                meeting.getStatus(),
                meeting.getStartedAt(),
                meeting.getEndedAt(),
                meeting.getCreatedAt()
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record MeetingCreateRequest(@Size(max = 200) String title,
                                       @Size(max = 32) String provider,
                                       @Size(max = 255) String joinUrl) {
    }

    public record MeetingResponse(Long id,
                                  Long docId,
                                  Long hostId,
                                  String title,
                                  String provider,
                                  String joinUrl,
                                  MeetingStatus status,
                                  LocalDateTime startedAt,
                                  LocalDateTime endedAt,
                                  LocalDateTime createdAt) {
    }
}
