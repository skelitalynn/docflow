package com.docflow.user;

import com.docflow.common.BaseEntity;
import com.docflow.common.SystemRole;
import com.docflow.common.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//对应数据库表t_user

//JPA实体类，将数据库表映射为Java对象
//@Entity：声明这是一个JPA实体类
//@Table：指定数据库表名
//@UniqueConstraint：指定唯一约束
//@Getter：自动生成getter方法
//@Setter：自动生成setter方法
//@NoArgsConstructor：自动生成无参构造函数
//@AllArgsConstructor：自动生成全参构造函数
//@Builder：自动生成builder模式

@Entity
@Table(name = "t_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_user_phone", columnNames = "phone")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {
    //主键，自动生成
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(length = 128)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "system_role", nullable = false)
    private SystemRole systemRole;

    @Column(length = 64)
    private String nickname;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(nullable = false)
    private UserStatus status;
}
