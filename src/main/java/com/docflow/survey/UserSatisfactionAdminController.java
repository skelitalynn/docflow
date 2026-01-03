package com.docflow.survey;

import com.docflow.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/surveys")
public class UserSatisfactionAdminController {
    private final UserSatisfactionService satisfactionService;

    public UserSatisfactionAdminController(UserSatisfactionService satisfactionService) {
        this.satisfactionService = satisfactionService;
    }

    @GetMapping
    public PageResponse<UserSatisfactionAdminResponse> list(@RequestParam(value = "page", defaultValue = "0") int page,
                                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<UserSatisfaction> surveys = satisfactionService.list(PageRequest.of(page, size));
        List<UserSatisfactionAdminResponse> items = surveys.map(this::toResponse).getContent();
        return new PageResponse<>(items, surveys.getNumber(), surveys.getSize(),
                surveys.getTotalElements(), surveys.getTotalPages());
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        UserSatisfactionService.Stats stats = satisfactionService.stats();
        return new StatsResponse(stats.averageRating(), stats.counts());
    }

    private UserSatisfactionAdminResponse toResponse(UserSatisfaction survey) {
        return new UserSatisfactionAdminResponse(
                survey.getId(),
                survey.getUser().getId(),
                survey.getUser().getNickname(),
                survey.getRating(),
                survey.getComment(),
                survey.getCreatedAt()
        );
    }

    public record UserSatisfactionAdminResponse(Long id,
                                                Long userId,
                                                String nickname,
                                                int rating,
                                                String comment,
                                                LocalDateTime createdAt) {
    }

    public record StatsResponse(double averageRating,
                                List<UserSatisfactionService.RatingCount> counts) {
    }
}
