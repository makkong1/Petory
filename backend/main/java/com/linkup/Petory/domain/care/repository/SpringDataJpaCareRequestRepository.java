package com.linkup.Petory.domain.care.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.care.dto.CareRequestListView;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.global.annotation.RepositoryMethod;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaCareRequestAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
public interface SpringDataJpaCareRequestRepository extends JpaRepository<CareRequest, Long> {

    @RepositoryMethod("펫케어 요청: 사용자별 목록 조회")
    @Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications WHERE cr.user = :user AND cr.isDeleted = false AND u.isDeleted = false AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspendedUntil <= CURRENT_TIMESTAMP)) ORDER BY cr.createdAt DESC")
    List<CareRequest> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    // 전체 케어 요청 조회 - 작성자도 활성 상태여야 함
    @RepositoryMethod("펫케어 요청: 전체 목록 조회")
    @Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications WHERE cr.isDeleted = false AND u.isDeleted = false AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspendedUntil <= CURRENT_TIMESTAMP)) ORDER BY cr.createdAt DESC")
    List<CareRequest> findAllActiveRequests();

    // 상태별 케어 요청 조회 - 작성자도 활성 상태여야 함
    // [1단계 최적화] CareApplication N+1 문제 해결: LEFT JOIN FETCH cr.applications 추가
    // [3단계 최적화] PetVaccination N+1 문제 해결: @BatchSize 사용 (Hibernate 중첩 컬렉션 제한으로 인해 FETCH JOIN 제거)
    @RepositoryMethod("펫케어 요청: 상태별 목록 조회")
    @Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications WHERE cr.status = :status AND cr.isDeleted = false AND u.isDeleted = false AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspendedUntil <= CURRENT_TIMESTAMP)) ORDER BY cr.createdAt DESC")
    List<CareRequest> findByStatusAndIsDeletedFalse(@Param("status") CareRequestStatus status);

    // FULLTEXT로 idx 목록만 조회 후, 어댑터에서 JOIN FETCH로 재조회(순서 유지·N+1 방지).
    @RepositoryMethod("펫케어 요청: FULLTEXT 검색 — idx 목록")
    @Query(value = "SELECT cr.idx FROM carerequest cr "
                    + "INNER JOIN users u ON u.idx = cr.user_idx "
                    + "WHERE (cr.is_deleted IS NULL OR cr.is_deleted = 0) "
                    + "AND (u.is_deleted IS NULL OR u.is_deleted = 0) "
                    + "AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspended_until <= NOW())) "
                    + "AND MATCH(cr.title, cr.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE) "
                    + "ORDER BY cr.created_at DESC",
            nativeQuery = true)
    List<Long> findIdxByFulltextKeyword(@Param("keyword") String keyword);

    @RepositoryMethod("펫케어 요청: idx 목록으로 연관 FETCH 조회")
    @Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications WHERE cr.idx IN :ids")
    List<CareRequest> findByIdxInWithAssociations(@Param("ids") Collection<Long> ids);

    // 날짜가 지났고 특정 상태인 요청 조회 (스케줄러용)
    @RepositoryMethod("펫케어 요청: 만료된 요청 조회 (스케줄러)")
    @Query("SELECT DISTINCT cr FROM CareRequest cr "
                    + "JOIN FETCH cr.user "
                    + "LEFT JOIN FETCH cr.applications a "
                    + "LEFT JOIN FETCH a.provider "
                    + "WHERE cr.date < :now AND cr.status IN :statuses")
    List<CareRequest> findByDateBeforeAndStatusIn(
                    @Param("now") LocalDateTime now,
                    @Param("statuses") List<CareRequestStatus> statuses);

    // 단일 케어 요청 조회 (작성자 포함) - 수정/삭제 시 권한 확인용
    @RepositoryMethod("펫케어 요청: 단건 조회 (작성자 포함)")
    @Query("SELECT cr FROM CareRequest cr JOIN FETCH cr.user WHERE cr.idx = :idx")
    Optional<CareRequest> findByIdWithUser(@Param("idx") Long idx);

    // 단일 케어 요청 조회 (펫 정보 및 지원 정보 포함, provider N+1 방지)
    // [3단계 최적화] PetVaccination N+1 문제 해결: @BatchSize 사용 (Hibernate 중첩 컬렉션 제한으로 인해 FETCH JOIN 제거)
    @RepositoryMethod("펫케어 요청: 단건 조회 (지원 목록 포함)")
    @Query("SELECT cr FROM CareRequest cr LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.user LEFT JOIN FETCH cr.applications a LEFT JOIN FETCH a.provider WHERE cr.idx = :idx")
    Optional<CareRequest> findByIdWithApplications(@Param("idx") Long idx);

    // 통계용
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // [FIX] 케어 완료 시각(completedAt)으로 집계 — '당일 실제 완료 건수' 정확히 반영
    @Query("SELECT COUNT(cr) FROM CareRequest cr WHERE cr.completedAt BETWEEN :start AND :end AND cr.isDeleted = false")
    long countByCompletedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // location: 접두사 일치만 허용 (LIKE '값%') — B-tree 인덱스(users.location) 활용 가능. 부분 문자열(중간 일치)은 제외.
    @RepositoryMethod("펫케어 요청: 페이징 전체 조회")
    @Query(value = "SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE cr.isDeleted = false AND u.isDeleted = false AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspendedUntil <= CURRENT_TIMESTAMP)) AND (:location IS NULL OR :location = '' OR (u.location IS NOT NULL AND u.location LIKE CONCAT(:location, '%'))) ORDER BY cr.createdAt DESC",
           countQuery = "SELECT COUNT(cr) FROM CareRequest cr JOIN cr.user u WHERE cr.isDeleted = false AND u.isDeleted = false AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspendedUntil <= CURRENT_TIMESTAMP)) AND (:location IS NULL OR :location = '' OR (u.location IS NOT NULL AND u.location LIKE CONCAT(:location, '%')))")
    Page<CareRequest> findAllActiveRequestsWithPaging(@Param("location") String location, Pageable pageable);

    @RepositoryMethod("펫케어 요청: 페이징 상태별 조회")
    @Query(value = "SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE cr.status = :status AND cr.isDeleted = false AND u.isDeleted = false AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspendedUntil <= CURRENT_TIMESTAMP)) AND (:location IS NULL OR :location = '' OR (u.location IS NOT NULL AND u.location LIKE CONCAT(:location, '%'))) ORDER BY cr.createdAt DESC",
           countQuery = "SELECT COUNT(cr) FROM CareRequest cr JOIN cr.user u WHERE cr.status = :status AND cr.isDeleted = false AND u.isDeleted = false AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspendedUntil <= CURRENT_TIMESTAMP)) AND (:location IS NULL OR :location = '' OR (u.location IS NOT NULL AND u.location LIKE CONCAT(:location, '%')))")
    Page<CareRequest> findByStatusAndIsDeletedFalseWithPaging(@Param("status") CareRequestStatus status, @Param("location") String location, Pageable pageable);

    // [오버페칭 제거] 지도(getNearby) 전용 projection.
    // 기존 SELECT cr.* + 컨버터가 작성자 전체·중첩 pet(파일 조회)·applications(@BatchSize 추가 쿼리)까지 로딩했으나,
    // 지도 레이어가 실제로 쓰는 14컬럼만 JOIN·SELECT 한다(연관 오버페칭+컬럼 오버페칭 동시 제거). WHERE/ORDER는 기존과 동일.
    @RepositoryMethod("펫케어 요청: 반경 기반 근처 요청 조회 (projection)")
    @Query(value = "SELECT cr.idx AS idx, cr.title AS title, cr.description AS description, cr.`date` AS `date`, " +
                    "  cr.schedule_mode AS scheduleMode, cr.estimated_duration_minutes AS estimatedDurationMinutes, " +
                    "  cr.offered_coins AS offeredCoins, cr.status AS status, " +
                    "  cr.latitude AS latitude, cr.longitude AS longitude, cr.address AS address, " +
                    "  u.idx AS userId, u.username AS username, p.pet_name AS petName " +
                    "FROM carerequest cr " +
                    "INNER JOIN users u ON u.idx = cr.user_idx " +
                    "LEFT JOIN pets p ON p.idx = cr.pet_idx " +
                    "WHERE cr.is_deleted = false " +
                    "AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspended_until <= NOW())) " +
                    "AND u.is_deleted = false " +
                    "AND cr.latitude IS NOT NULL " +
                    "AND cr.status IN ('OPEN', 'IN_PROGRESS') " +
                    // 이전에는 latitude/longitude 를 BETWEEN 으로 걸렀다. 인덱스를 타지 못해 풀스캔이었고,
                    // 옵티마이저가 위도·경도를 독립 조건으로 곱해 선택도를 208배 오판했다(예상 3.77행 / 실제 783행).
                    // meetup 과 동일하게 SPATIAL 인덱스(geo_point)를 타는 ST_Within 으로 바꾼다.
                    // ST_Within 이 사각형으로 후보를 좁히고(인덱스), ST_Distance_Sphere 가 정확한 반경으로 거른다.
                    "AND ST_Within(cr.geo_point, ST_GeomFromText(CONCAT('POLYGON((', " +
                    ":lat - (:radius / 111.0), ' ', :lng - (:radius / (111.0 * COS(RADIANS(:lat)))), ', ', " +
                    ":lat - (:radius / 111.0), ' ', :lng + (:radius / (111.0 * COS(RADIANS(:lat)))), ', ', " +
                    ":lat + (:radius / 111.0), ' ', :lng + (:radius / (111.0 * COS(RADIANS(:lat)))), ', ', " +
                    ":lat + (:radius / 111.0), ' ', :lng - (:radius / (111.0 * COS(RADIANS(:lat)))), ', ', " +
                    ":lat - (:radius / 111.0), ' ', :lng - (:radius / (111.0 * COS(RADIANS(:lat)))), '))'), " +
                    "4326)) " +
                    "AND ST_Distance_Sphere(cr.geo_point, ST_GeomFromText(" +
                    "CONCAT('POINT(', :lat, ' ', :lng, ')'), 4326)) <= (:radius * 1000) " +
                    "ORDER BY cr.created_at DESC " +
                    "LIMIT :limit", nativeQuery = true)
    List<CareRequestListView> findNearbyCareRequests(@Param("lat") Double lat,
                    @Param("lng") Double lng,
                    @Param("radius") Double radius,
                    @Param("limit") int limit);

    /** 이벤트 리스너용: BANNED 사용자의 OPEN 케어 취소 처리 */
    List<CareRequest> findByUser_IdxAndStatusAndIsDeletedFalse(Long userIdx, CareRequestStatus status);

    // 키워드 검색: FULLTEXT (인덱스 docs/migration/db/indexes.sql). 페이징은 Spring이 LIMIT/OFFSET 처리.
    @RepositoryMethod("펫케어 요청: 페이징 키워드 검색 (FULLTEXT)")
    @Query(value = "SELECT cr.* FROM carerequest cr "
                    + "INNER JOIN users u ON u.idx = cr.user_idx "
                    + "WHERE (cr.is_deleted IS NULL OR cr.is_deleted = 0) "
                    + "AND (u.is_deleted IS NULL OR u.is_deleted = 0) "
                    + "AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspended_until <= NOW())) "
                    + "AND MATCH(cr.title, cr.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE) "
                    + "ORDER BY cr.created_at DESC",
            countQuery = "SELECT COUNT(cr.idx) FROM carerequest cr "
                    + "INNER JOIN users u ON u.idx = cr.user_idx "
                    + "WHERE (cr.is_deleted IS NULL OR cr.is_deleted = 0) "
                    + "AND (u.is_deleted IS NULL OR u.is_deleted = 0) "
                    + "AND (u.status = 'ACTIVE' OR (u.status = 'SUSPENDED' AND u.suspended_until <= NOW())) "
                    + "AND MATCH(cr.title, cr.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE)",
            nativeQuery = true)
    Page<CareRequest> searchWithPaging(@Param("keyword") String keyword, Pageable pageable);

    // JOIN FETCH 가 없으면 목록 N건마다 user·pet 을 지연 로딩해 N+1 이 난다.
    // 공개 API(findAllActiveRequestsWithPaging)는 이미 fetch join 을 쓰고 있었는데 관리자 경로만 빠져 있었다.
    // Page<> + JOIN FETCH 는 countQuery 를 명시하지 않으면 Hibernate 가 fetch join 을 물고 COUNT 를 만든다.
    @RepositoryMethod("펫케어 요청: 관리자 필터 페이징 조회 (keyword 없을 때)")
    @Query(value = "SELECT r FROM CareRequest r JOIN FETCH r.user LEFT JOIN FETCH r.pet WHERE " +
           "(:status IS NULL OR CAST(r.status AS string) = :status) AND " +
           "(:deleted IS NULL OR r.isDeleted = :deleted) " +
           "ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM CareRequest r WHERE " +
           "(:status IS NULL OR CAST(r.status AS string) = :status) AND " +
           "(:deleted IS NULL OR r.isDeleted = :deleted)")
    Page<CareRequest> findAllForAdmin(
            @Param("status") String status,
            @Param("deleted") Boolean deleted,
            Pageable pageable);

    @RepositoryMethod("펫케어 요청: 관리자 키워드 페이징 조회 (FULLTEXT title/description)")
    @Query(value =
           "SELECT * FROM carerequest r " +
           "WHERE (:status IS NULL OR r.status = :status) " +
           "AND (:deleted IS NULL OR r.is_deleted = :deleted) " +
           "AND MATCH(r.title, r.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE) " +
           "ORDER BY r.created_at DESC",
           countQuery =
           "SELECT COUNT(*) FROM carerequest r " +
           "WHERE (:status IS NULL OR r.status = :status) " +
           "AND (:deleted IS NULL OR r.is_deleted = :deleted) " +
           "AND MATCH(r.title, r.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE)",
           nativeQuery = true)
    Page<CareRequest> findAllForAdminWithKeyword(
            @Param("status") String status,
            @Param("deleted") Boolean deleted,
            @Param("keyword") String keyword,
            Pageable pageable);

    @RepositoryMethod("펫케어 요청: 상태+기간별 통계 (취소 케어 집계용)")
    long countByStatusAndUpdatedAtBetween(CareRequestStatus status, LocalDateTime start, LocalDateTime end);
}
