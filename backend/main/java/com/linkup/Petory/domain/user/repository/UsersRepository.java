package com.linkup.Petory.domain.user.repository;

import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.Users;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByUsername(String username);

    Optional<Users> findByNickname(String nickname);

    Optional<Users> findByEmail(String email);

    // 로그인용 아이디(String 타입 id 필드)로 조회
    @Query("SELECT u FROM Users u WHERE u.id = :id")
    Optional<Users> findByIdString(@Param("id") String id);

    // Refresh Token으로 사용자 조회
    Optional<Users> findByRefreshToken(String refreshToken);

    // 통계용
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByLastLoginAtBetween(LocalDateTime start, LocalDateTime end);

    // 경고 횟수 원자적 증가 (동시성 문제 해결)
    @Modifying
    @Query("UPDATE Users u SET u.warningCount = u.warningCount + 1 WHERE u.idx = :userId")
    int incrementWarningCount(@Param("userId") Long userId);
}
