package com.docflow.survey;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserSatisfactionRepository extends JpaRepository<UserSatisfaction, Long> {
    Page<UserSatisfaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select avg(s.rating) from UserSatisfaction s")
    Double avgRating();

    @Query("select s.rating as rating, count(s) as count from UserSatisfaction s group by s.rating")
    List<RatingCount> countByRating();

    interface RatingCount {
        Integer getRating();

        long getCount();
    }
}
