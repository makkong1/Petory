package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.location.entity.LocationServiceReview;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaLocationServiceReviewRepository extends JpaRepository<LocationServiceReview, Long> {

    /**
     * 서비스별 리뷰 목록 projection 페이징 조회. user를 통째로(JOIN FETCH) 로딩하던 것을 idx/username
     * 2컬럼만 SELECT하도록 축소하고, 전건 반환 대신 DB 페이징으로 바꿔(장소당 리뷰 누적과 무관하게 조회량 일정) '더보기'
     * 누적 방식으로 소비한다. 목록에선 serviceName을 쓰지 않으므로 service를 조인하지 않고 null로 둔다.
     */
    @RepositoryMethod("장소 리뷰: 서비스별 목록 projection 페이징")
    @Query("SELECT new com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO("
            + "  r.idx, r.service.idx, CAST(NULL AS string), u.idx, u.username, "
            + "  r.rating, r.comment, r.createdAt, r.updatedAt, r.isDeleted, r.deletedAt) "
            + "FROM LocationServiceReview r JOIN r.user u "
            + "WHERE r.service.idx = :serviceIdx AND r.isDeleted = false "
            + "AND u.isDeleted = false AND u.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED "
            + "ORDER BY r.createdAt DESC")
    Page<LocationServiceReviewDTO> findReviewListItems(@Param("serviceIdx") Long serviceIdx, Pageable pageable);

    @RepositoryMethod("장소 리뷰: 사용자별 목록 조회")
    @Query("SELECT r FROM LocationServiceReview r JOIN FETCH r.service JOIN FETCH r.user u WHERE "
            + "r.user.idx = :userIdx AND "
            + "r.isDeleted = false AND "
            + "u.isDeleted = false AND u.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED "
            + "ORDER BY r.createdAt DESC")
    List<LocationServiceReview> findByUserIdxOrderByCreatedAtDesc(@Param("userIdx") Long userIdx);

    @RepositoryMethod("장소 리뷰: 단건 조회 (user, service 포함)")
    @Query("SELECT r FROM LocationServiceReview r JOIN FETCH r.user JOIN FETCH r.service WHERE r.idx = :idx")
    Optional<LocationServiceReview> findByIdWithUserAndService(@Param("idx") Long idx);

    @RepositoryMethod("장소 리뷰: 서비스별 평균 평점")
    @Query("SELECT AVG(r.rating) FROM LocationServiceReview r JOIN r.user u WHERE "
            + "r.service.idx = :serviceIdx AND "
            + "r.isDeleted = false AND "
            + "u.isDeleted = false AND u.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED")
    Optional<Double> findAverageRatingByServiceIdx(@Param("serviceIdx") Long serviceIdx);

    @RepositoryMethod("장소 리뷰: 서비스+사용자 리뷰 작성 여부")
    @Query("SELECT COUNT(r) > 0 FROM LocationServiceReview r WHERE "
            + "r.service.idx = :serviceIdx AND "
            + "r.user.idx = :userIdx AND "
            + "r.isDeleted = false")
    boolean existsByServiceIdxAndUserIdx(@Param("serviceIdx") Long serviceIdx, @Param("userIdx") Long userIdx);
}
