package com.docflow.survey;

import com.docflow.common.BaseEntity;
import com.docflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 用户满意度调查实体：记录评分与可选反馈
@Entity
@Table(name = "t_user_satisfaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSatisfaction extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "survey_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 评分：1~5
    @Column(nullable = false)
    private int rating;

    // 文字反馈：可选，长度最多 1000
    @Column(length = 1000)
    private String comment;
}
