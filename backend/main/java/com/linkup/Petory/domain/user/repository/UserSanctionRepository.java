package com.linkup.Petory.domain.user.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.user.entity.UserSanction;
import com.linkup.Petory.domain.user.entity.Users;

public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    /**
     * 유저의 활성 제재 조회 (현재 유효한 제재)
     */
    @Query("SELECT s FROM UserSanction s WHERE s.user.idx = :userId " +
           "AND (s.endsAt IS NULL OR s.endsAt > :now) " +
           "AND s.startsAt <= :now " +
           "ORDER BY s.createdAt DESC")
    List<UserSanction> findActiveSanctionsByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 유저의 경고 횟수 조회
     */
    @Query("SELECT COUNT(s) FROM UserSanction s WHERE s.user.idx = :userId " +
           "AND s.sanctionType = 'WARNING'")
    long countWarningsByUserId(@Param("userId") Long userId);

    /**
     * 유저의 모든 제재 이력 조회
     */
    List<UserSanction> findByUserOrderByCreatedAtDesc(Users user);

    /**
     * 만료되어 해제해야 할 이용제한 조회
     */
    @Query("SELECT s FROM UserSanction s WHERE s.sanctionType = 'SUSPENSION' " +
           "AND s.endsAt IS NOT NULL AND s.endsAt <= :now " +
           "AND s.user.status = 'SUSPENDED'")
    List<UserSanction> findExpiredSuspensions(@Param("now") LocalDateTime now);
}

