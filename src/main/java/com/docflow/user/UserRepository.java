package com.docflow.user;

import com.docflow.common.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

//数据访问层DAO，继承了JpaRepository和JpaSpecificationExecutor，提供基础CRUD + 按 email/phone 查询


public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    //根据邮箱查找用户
    Optional<User> findByEmail(String email);

    //根据手机号查找用户
    Optional<User> findByPhone(String phone);

    //根据邮箱或手机号查找用户

    Optional<User> findByEmailOrPhone(String email, String phone);

    //判断是否存在指定系统角色用户
    boolean existsBySystemRole(SystemRole systemRole);
}
