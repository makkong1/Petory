package com.linkup.Petory.domain.user.repository;

import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaUsersAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaUsersRepository extends JpaRepository<Users, Long> {

    /**
     * 탈퇴하지 않은 사용자만 조회 (Soft Delete 필터링)
     */
    @Query("SELECT u FROM Users u WHERE u.username = :username AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<Users> findByUsername(@Param("username") String username);

    /**
     * 탈퇴하지 않은 사용자만 조회 (Soft Delete 필터링)
     */
    @Query("SELECT u FROM Users u WHERE u.nickname = :nickname AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<Users> findByNickname(@Param("nickname") String nickname);

    /**
     * 탈퇴하지 않은 사용자만 조회 (Soft Delete 필터링)
     */
    @Query("SELECT u FROM Users u WHERE u.email = :email AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<Users> findByEmail(@Param("email") String email);

    /**
     * 로그인용 아이디(String 타입 id 필드)로 조회
     */
    @Query("SELECT u FROM Users u WHERE u.id = :id")
    Optional<Users> findByIdString(@Param("id") String id);

    /**
     * Refresh Token으로 사용자 조회
     */
    Optional<Users> findByRefreshToken(String refreshToken);

    /**
     * 통계용: 특정 기간 동안 생성된 사용자 수
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 통계용: 특정 기간 동안 로그인한 사용자 수
     */
    long countByLastLoginAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 경고 횟수 원자적 증가 (동시성 문제 해결)
     * @return 업데이트된 행 수
     */
    @Modifying
    @Query("UPDATE Users u SET u.warningCount = u.warningCount + 1 WHERE u.idx = :userId")
    int incrementWarningCount(@Param("userId") Long userId);
}
