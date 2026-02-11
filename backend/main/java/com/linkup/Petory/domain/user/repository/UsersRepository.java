package com.linkup.Petory.domain.user.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Users 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaUsersAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface UsersRepository {

    Users save(Users user);

    Optional<Users> findById(Long id);

    void delete(Users user);

    List<Users> findAll();

    Page<Users> findAll(Pageable pageable);

    void deleteById(Long id);

    /**
     * 탈퇴하지 않은 사용자만 조회 (Soft Delete 필터링)
     */
    Optional<Users> findByUsername(String username);

    /**
     * 탈퇴하지 않은 사용자만 조회 (Soft Delete 필터링)
     */
    Optional<Users> findByNickname(String nickname);

    /**
     * 탈퇴하지 않은 사용자만 조회 (Soft Delete 필터링)
     */
    Optional<Users> findByEmail(String email);

    /**
     * 로그인용 아이디(String 타입 id 필드)로 조회
     */
    Optional<Users> findByIdString(String id);

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
     * 
     * @return 업데이트된 행 수
     */
    int incrementWarningCount(Long userId);

    /**
     * 비관적 락을 사용한 사용자 조회 (동시성 제어용)
     * 코인 차감 시 Race Condition 방지를 위해 사용
     */
    Optional<Users> findByIdForUpdate(Long idx);

    /**
     * 사용자 역할만 조회 (경량 조회용, 삭제 권한 검증 등)
     */
    Optional<Role> findRoleByIdx(Long idx);
}
