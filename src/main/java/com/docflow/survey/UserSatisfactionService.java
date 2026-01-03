package com.docflow.survey;

import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.NotFoundException;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserSatisfactionService {
    private final UserSatisfactionRepository satisfactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserSatisfactionService(UserSatisfactionRepository satisfactionRepository,
                                   UserRepository userRepository,
                                   AuditService auditService) {
        this.satisfactionRepository = satisfactionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public UserSatisfaction submit(Long userId, int rating, String comment, String ip) {
        if (rating < 1 || rating > 5) {
            throw new BadRequestException("Rating must be 1-5");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UserSatisfaction entity = UserSatisfaction.builder()
                .user(user)
                .rating(rating)
                .comment(comment == null || comment.isBlank() ? null : comment.trim())
                .build();
        UserSatisfaction saved = satisfactionRepository.save(entity);
        auditService.record(user, "satisfaction_submit", "survey", saved.getId(), null, true, null, ip,
                java.util.Map.of("rating", rating));
        return saved;
    }

    public Page<UserSatisfaction> list(Pageable pageable) {
        return satisfactionRepository.findAll(pageable);
    }

    public Page<UserSatisfaction> listByUser(Long userId, Pageable pageable) {
        return satisfactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Stats stats() {
        Double avg = satisfactionRepository.avgRating();
        List<UserSatisfactionRepository.RatingCount> counts = satisfactionRepository.countByRating();
        List<RatingCount> items = new ArrayList<>();
        for (UserSatisfactionRepository.RatingCount count : counts) {
            items.add(new RatingCount(count.getRating(), count.getCount()));
        }
        return new Stats(avg == null ? 0.0 : avg, items);
    }

    public record RatingCount(Integer rating, long count) {
    }

    public record Stats(double averageRating, List<RatingCount> counts) {
    }
}
