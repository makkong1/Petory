package com.linkup.Petory.domain.user.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.user.entity.LoginEvent;
import com.linkup.Petory.global.annotation.RepositoryMethod;

public interface SpringDataJpaLoginEventRepository extends JpaRepository<LoginEvent, Long> {

    @RepositoryMethod("로그인 이벤트: 기간 내 DISTINCT 사용자 수 (DAU 집계용)")
    @Query("SELECT COUNT(DISTINCT l.user.idx) FROM LoginEvent l WHERE l.loginAt BETWEEN :start AND :end")
    long countDistinctUsersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
