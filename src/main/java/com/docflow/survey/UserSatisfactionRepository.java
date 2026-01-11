package com.docflow.survey;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserSatisfactionRepository extends JpaRepository<UserSatisfaction, Long> {
    // 按用户分页查询满意度记录
    Page<UserSatisfaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 统计平均评分
    @Query("select avg(s.rating) from UserSatisfaction s")
    Double avgRating();

    // 按评分聚合统计人数
    @Query("select s.rating as rating, count(s) as count from UserSatisfaction s group by s.rating")
    List<RatingCount> countByRating();

    // 评分统计投影
    interface RatingCount {
        Integer getRating();

        long getCount();
    }
}
