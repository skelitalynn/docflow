package com.docflow.survey;

import com.docflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/surveys")
public class UserSatisfactionController {
    private final UserSatisfactionService satisfactionService;

    public UserSatisfactionController(UserSatisfactionService satisfactionService) {
        this.satisfactionService = satisfactionService;
    }

    @PostMapping
    public UserSatisfactionResponse submit(@Valid @RequestBody SubmitRequest request,
                                           HttpServletRequest httpRequest) {
        UserSatisfaction saved = satisfactionService.submit(SecurityUtils.getCurrentUserId(),
                request.rating(),
                request.comment(),
                clientIp(httpRequest));
        return new UserSatisfactionResponse(saved.getId(),
                saved.getUser().getId(),
                saved.getRating(),
                saved.getComment(),
                saved.getCreatedAt());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public record SubmitRequest(@Min(1) @Max(5) int rating,
                                @Size(max = 1000) String comment) {
    }

    public record UserSatisfactionResponse(Long id,
                                           Long userId,
                                           int rating,
                                           String comment,
                                           LocalDateTime createdAt) {
    }
}
