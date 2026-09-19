package com.linkup.Petory.domain.user.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 *
 * 이 인터페이스는 JpaUsersAdapter 내부에서만 사용되며, 도메인 레이어에서는 직접 사용하지 않습니다.
 *
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaUsersRepository extends JpaRepository<Users, Long> {

    @RepositoryMethod("사용자: username으로 조회")
    @Query("SELECT u FROM Users u WHERE u.username = :username AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<Users> findByUsername(@Param("username") String username);

    @RepositoryMethod("사용자: nickname으로 조회")
    @Query("SELECT u FROM Users u WHERE u.nickname = :nickname AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<Users> findByNickname(@Param("nickname") String nickname);

    @RepositoryMethod("사용자: email으로 조회")
    @Query("SELECT u FROM Users u WHERE u.email = :email AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<Users> findByEmail(@Param("email") String email);

    /**
     * 닉네임/사용자명/이메일 중복 검사 (1회 쿼리로 통합) [리팩토링] findByNickname + findByUsername +
     * findByEmail 3회 → 1회 탈퇴하지 않은 사용자만 조회 (Soft Delete 필터링)
     */
    @RepositoryMethod("사용자: 닉네임/username/email 중복 검사")
    @Query("SELECT u FROM Users u WHERE (u.nickname = :nickname OR u.username = :username OR u.email = :email) AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    List<Users> findByNicknameOrUsernameOrEmail(
            @Param("nickname") String nickname,
            @Param("username") String username,
            @Param("email") String email,
            Pageable pageable);

    @RepositoryMethod("사용자: 로그인 ID(String)로 조회")
    @Query("SELECT u FROM Users u WHERE u.id = :id")
    Optional<Users> findByIdString(@Param("id") String id);

    @RepositoryMethod("사용자: 로그인 ID(String)로 조회 (소프트 삭제 제외)")
    @Query("SELECT u FROM Users u WHERE u.id = :id AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<Users> findActiveByIdString(@Param("id") String id);

    @RepositoryMethod("사용자: 로그인 ID(String)로 idx 스칼라 조회 (경량)")
    @Query("SELECT u.idx FROM Users u WHERE u.id = :id")
    Optional<Long> findIdxByIdString(@Param("id") String id);

    @RepositoryMethod("사용자: RefreshToken으로 조회 (소프트 삭제 제외)")
    @Query("SELECT u FROM Users u WHERE u.refreshToken = :refreshToken AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<Users> findActiveByRefreshToken(@Param("refreshToken") String refreshToken);

    @RepositoryMethod("사용자: 기간별 가입 수 통계")
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 경고 횟수 원자적 증가 (동시성 문제 해결)
     *
     * @return 업데이트된 행 수
     */
    @RepositoryMethod("사용자: 경고 횟수 증가")
    @Modifying
    @Query("UPDATE Users u SET u.warningCount = u.warningCount + 1 WHERE u.idx = :userId")
    int incrementWarningCount(@Param("userId") Long userId);

    /**
     * 휴면 계정 일괄 전환 (배치용) lastLoginAt이 있으면 그 값을, 없으면(가입 후 미로그인) createdAt을 기준으로
     * 판정
     *
     * @return 업데이트된 행 수
     */
    @RepositoryMethod("사용자: 휴면 계정 일괄 전환 (배치)")
    @Modifying
    @Query("UPDATE Users u SET u.isDormant = true, u.dormantAt = :now "
            + "WHERE u.isDormant = false AND u.isDeleted = false AND ("
            + "  (u.lastLoginAt IS NOT NULL AND u.lastLoginAt < :cutoff) OR "
            + "  (u.lastLoginAt IS NULL AND u.createdAt < :cutoff)"
            + ")")
    int markDormantUsers(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    /**
     * 비관적 락을 사용한 사용자 조회 (동시성 제어용) 코인 차감 시 Race Condition 방지를 위해 사용
     */
    @RepositoryMethod("사용자: 비관적 락 조회 (동시성 제어)")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Users u WHERE u.idx = :idx")
    Optional<Users> findByIdForUpdate(@Param("idx") Long idx);

    /**
     * 사용자 역할만 조회 (경량 조회용, 삭제 권한 검증 등) [리팩토링] getUser(User+Pet) 대체 - role 프로젝션만
     * SELECT
     */
    @RepositoryMethod("사용자: 역할만 조회 (경량)")
    @Query("SELECT u.role FROM Users u WHERE u.idx = :idx")
    Optional<Role> findRoleByIdx(@Param("idx") Long idx);

    /**
     * 사용자 단건 조회 (펫 포함, Fetch Join) [리팩토링] getUserWithPets - User + Pet 1회 쿼리
     */
    @RepositoryMethod("사용자: idx로 조회 (펫 포함)")
    @Query("SELECT DISTINCT u FROM Users u LEFT JOIN FETCH u.pets WHERE u.idx = :idx")
    Optional<Users> findByIdWithPets(@Param("idx") Long idx);

    /**
     * 사용자 단건 조회 (펫 포함, Fetch Join) [리팩토링] getMyProfile - User + Pet 1회 쿼리
     */
    @RepositoryMethod("사용자: id(String)로 조회 (펫 포함)")
    @Query("SELECT DISTINCT u FROM Users u LEFT JOIN FETCH u.pets WHERE u.id = :userId")
    Optional<Users> findByIdStringWithPets(@Param("userId") String userId);

    // 관리자 필터 페이징(findAllForAdmin·findAdminUserListItems)은 QueryDSL로 이관됨(JpaUsersAdapter).
    // :param IS NULL OR 안티패턴 제거 + WHERE 절 공유 목적. 이력: docs/refactoring/querydsl/

    @RepositoryMethod("사용자: 역할+기간별 통계 (신규 서비스 제공자 집계용)")
    long countByRoleAndCreatedAtBetween(Role role, LocalDateTime start, LocalDateTime end);

    /**
     * 활동 가능한 서비스 제공자 전원. 지역 일치는 호출부가 애플리케이션에서 거른다.
     *
     * <p>지역을 SQL 로 못 거르는 이유: 활동지역이 자유 입력이라 표기가 흔들린다
     * ("서울 강남구" 240명 / "서울특별시 강남구" 2명). 시·도 표기를 빼고 시/군/구 토큰만
     * 뽑아 비교해야 하는데, 그 토큰 추출을 SQL 로 옮기면 쿼리가 훨씬 어려워진다.
     * 근본 해결은 프로필 입력을 선택식으로 고정하는 것이고, 기존 데이터 정리가 딸려와 별건이다.
     *
     * <p>⚠️ 천장: 제공자 전원을 읽어 앱에서 거른다. 현재 492명이라 무해하지만, 제공자가
     * 수만 명이 되면 지역을 정규화해 컬럼으로 뽑고 인덱스를 거는 쪽으로 옮겨야 한다.
     */
    @Query("""
            SELECT u FROM Users u
            WHERE u.role = com.linkup.Petory.domain.user.entity.Role.SERVICE_PROVIDER
              AND u.isDeleted = false
              AND u.status = com.linkup.Petory.domain.user.entity.UserStatus.ACTIVE
              AND u.idx <> :excludeUserIdx
            """)
    List<Users> findActiveServiceProviders(@Param("excludeUserIdx") Long excludeUserIdx);
}
