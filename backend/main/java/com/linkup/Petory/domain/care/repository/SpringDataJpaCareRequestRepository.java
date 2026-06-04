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
    @Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications WHERE cr.user = :user AND cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY cr.createdAt DESC")
    List<CareRequest> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    // 전체 케어 요청 조회 - 작성자도 활성 상태여야 함
    @RepositoryMethod("펫케어 요청: 전체 목록 조회")
    @Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications WHERE cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY cr.createdAt DESC")
    List<CareRequest> findAllActiveRequests();

    // 상태별 케어 요청 조회 - 작성자도 활성 상태여야 함
    // [1단계 최적화] CareApplication N+1 문제 해결: LEFT JOIN FETCH cr.applications 추가
    // [3단계 최적화] PetVaccination N+1 문제 해결: @BatchSize 사용 (Hibernate 중첩 컬렉션 제한으로 인해 FETCH JOIN 제거)
    @RepositoryMethod("펫케어 요청: 상태별 목록 조회")
    @Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications WHERE cr.status = :status AND cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY cr.createdAt DESC")
    List<CareRequest> findByStatusAndIsDeletedFalse(@Param("status") CareRequestStatus status);

    // FULLTEXT로 idx 목록만 조회 후, 어댑터에서 JOIN FETCH로 재조회(순서 유지·N+1 방지).
    @RepositoryMethod("펫케어 요청: FULLTEXT 검색 — idx 목록")
    @Query(value = "SELECT cr.idx FROM carerequest cr "
                    + "INNER JOIN users u ON u.idx = cr.user_idx "
                    + "WHERE (cr.is_deleted IS NULL OR cr.is_deleted = 0) "
                    + "AND (u.is_deleted IS NULL OR u.is_deleted = 0) AND u.status = 'ACTIVE' "
                    + "AND MATCH(cr.title, cr.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE) "
                    + "ORDER BY cr.created_at DESC",
            nativeQuery = true)
    List<Long> findIdxByFulltextKeyword(@Param("keyword") String keyword);

    @RepositoryMethod("펫케어 요청: idx 목록으로 연관 FETCH 조회")
    @Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications WHERE cr.idx IN :ids")
    List<CareRequest> findByIdxInWithAssociations(@Param("ids") Collection<Long> ids);

    // 날짜가 지났고 특정 상태인 요청 조회 (스케줄러용)
    @RepositoryMethod("펫케어 요청: 만료된 요청 조회 (스케줄러)")
    @Query("SELECT cr FROM CareRequest cr WHERE cr.date < :now AND cr.status IN :statuses")
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

    // [DEPRECATED] 케어 예정일(date)로 집계 — 통계 오류 원인
    long countByDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, CareRequestStatus status);

    // [FIX] 케어 완료 시각(completedAt)으로 집계 — '당일 실제 완료 건수' 정확히 반영
    @Query("SELECT COUNT(cr) FROM CareRequest cr WHERE cr.completedAt BETWEEN :start AND :end AND cr.isDeleted = false")
    long countByCompletedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // location: 접두사 일치만 허용 (LIKE '값%') — B-tree 인덱스(users.location) 활용 가능. 부분 문자열(중간 일치)은 제외.
    @RepositoryMethod("펫케어 요청: 페이징 전체 조회")
    @Query(value = "SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' AND (:location IS NULL OR :location = '' OR (u.location IS NOT NULL AND u.location LIKE CONCAT(:location, '%'))) ORDER BY cr.createdAt DESC",
           countQuery = "SELECT COUNT(cr) FROM CareRequest cr JOIN cr.user u WHERE cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' AND (:location IS NULL OR :location = '' OR (u.location IS NOT NULL AND u.location LIKE CONCAT(:location, '%')))")
    Page<CareRequest> findAllActiveRequestsWithPaging(@Param("location") String location, Pageable pageable);

    @RepositoryMethod("펫케어 요청: 페이징 상태별 조회")
    @Query(value = "SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE cr.status = :status AND cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' AND (:location IS NULL OR :location = '' OR (u.location IS NOT NULL AND u.location LIKE CONCAT(:location, '%'))) ORDER BY cr.createdAt DESC",
           countQuery = "SELECT COUNT(cr) FROM CareRequest cr JOIN cr.user u WHERE cr.status = :status AND cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' AND (:location IS NULL OR :location = '' OR (u.location IS NOT NULL AND u.location LIKE CONCAT(:location, '%')))")
    Page<CareRequest> findByStatusAndIsDeletedFalseWithPaging(@Param("status") CareRequestStatus status, @Param("location") String location, Pageable pageable);

    @RepositoryMethod("펫케어 요청: 반경 기반 근처 요청 조회")
    @Query(value = "SELECT cr.* FROM carerequest cr " +
                    "WHERE cr.is_deleted = false " +
                    "AND cr.latitude IS NOT NULL " +
                    "AND cr.status IN ('OPEN', 'IN_PROGRESS') " +
                    "AND cr.latitude BETWEEN (:lat - :radius / 111.0) AND (:lat + :radius / 111.0) " +
                    "AND cr.longitude BETWEEN (:lng - :radius / (111.0 * cos(radians(:lat)))) AND (:lng + :radius / (111.0 * cos(radians(:lat)))) " +
                    "AND (6371 * acos(cos(radians(:lat)) * cos(radians(cr.latitude)) * " +
                    "    cos(radians(cr.longitude) - radians(:lng)) + " +
                    "    sin(radians(:lat)) * sin(radians(cr.latitude)))) <= :radius " +
                    "ORDER BY cr.created_at DESC " +
                    "LIMIT :limit", nativeQuery = true)
    List<CareRequest> findNearbyCareRequests(@Param("lat") Double lat,
                    @Param("lng") Double lng,
                    @Param("radius") Double radius,
                    @Param("limit") int limit);

    // 키워드 검색: FULLTEXT (인덱스 docs/migration/db/indexes.sql). 페이징은 Spring이 LIMIT/OFFSET 처리.
    @RepositoryMethod("펫케어 요청: 페이징 키워드 검색 (FULLTEXT)")
    @Query(value = "SELECT cr.* FROM carerequest cr "
                    + "INNER JOIN users u ON u.idx = cr.user_idx "
                    + "WHERE (cr.is_deleted IS NULL OR cr.is_deleted = 0) "
                    + "AND (u.is_deleted IS NULL OR u.is_deleted = 0) AND u.status = 'ACTIVE' "
                    + "AND MATCH(cr.title, cr.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE) "
                    + "ORDER BY cr.created_at DESC",
            countQuery = "SELECT COUNT(cr.idx) FROM carerequest cr "
                    + "INNER JOIN users u ON u.idx = cr.user_idx "
                    + "WHERE (cr.is_deleted IS NULL OR cr.is_deleted = 0) "
                    + "AND (u.is_deleted IS NULL OR u.is_deleted = 0) AND u.status = 'ACTIVE' "
                    + "AND MATCH(cr.title, cr.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE)",
            nativeQuery = true)
    Page<CareRequest> searchWithPaging(@Param("keyword") String keyword, Pageable pageable);

    @RepositoryMethod("펫케어 요청: 관리자 필터 페이징 조회 (keyword 없을 때)")
    @Query("SELECT r FROM CareRequest r WHERE " +
           "(:status IS NULL OR CAST(r.status AS string) = :status) AND " +
           "(:deleted IS NULL OR r.isDeleted = :deleted) " +
           "ORDER BY r.createdAt DESC")
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

