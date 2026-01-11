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

// 满意度调查业务：提交、列表与统计
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
        // 评分范围校验（1~5）
        if (rating < 1 || rating > 5) {
            throw new BadRequestException("Rating must be 1-5");
        }
        // 校验用户存在
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        // 保存评分与可选评论
        UserSatisfaction entity = UserSatisfaction.builder()
                .user(user)
                .rating(rating)
                .comment(comment == null || comment.isBlank() ? null : comment.trim())
                .build();
        UserSatisfaction saved = satisfactionRepository.save(entity);
        // 记录审计日志，便于统计与追溯
        auditService.record(user, "satisfaction_submit", "survey", saved.getId(), null, true, null, ip,
                java.util.Map.of("rating", rating));
        return saved;
    }

    public Page<UserSatisfaction> list(Pageable pageable) {
        // 管理员分页查看全部满意度记录
        return satisfactionRepository.findAll(pageable);
    }

    public Page<UserSatisfaction> listByUser(Long userId, Pageable pageable) {
        // 用户维度查询自己的满意度记录
        return satisfactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Stats stats() {
        // 统计平均分与各评分人数
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
