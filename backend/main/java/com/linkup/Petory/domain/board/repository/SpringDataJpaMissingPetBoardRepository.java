package com.linkup.Petory.domain.board.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 * 
 * 이 인터페이스는 JpaMissingPetBoardAdapter 내부에서만 사용되며,
 * 도메인 레이어에서는 직접 사용하지 않습니다.
 * 
 * JPA 특화 기능(쿼리 메서드, JPQL 등)은 이 인터페이스에 정의합니다.
 */
// [리팩토링] Admin 페이징 DB 레벨 필터링을 위해 JpaSpecificationExecutor 추가
public interface SpringDataJpaMissingPetBoardRepository extends JpaRepository<MissingPetBoard, Long>, JpaSpecificationExecutor<MissingPetBoard> {

    @RepositoryMethod("실종 제보: 전체 목록 조회")
    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findAllByOrderByCreatedAtDesc();

    @RepositoryMethod("실종 제보: 상태별 목록 조회")
    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.status = :status AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(@Param("status") MissingPetStatus status);

    @RepositoryMethod("실종 제보: 단건 조회 (작성자 포함)")
    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.idx = :id AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Optional<MissingPetBoard> findByIdWithUser(@Param("id") Long id);

    @RepositoryMethod("실종 제보: 사용자별 목록 조회")
    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.user = :user AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
    List<MissingPetBoard> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

    @RepositoryMethod("실종 제보: 전체 페이징")
    @Query(value = "SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM MissingPetBoard b JOIN b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Page<MissingPetBoard> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @RepositoryMethod("실종 제보: 상태별 페이징")
    @Query(value = "SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.status = :status AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM MissingPetBoard b JOIN b.user u WHERE b.status = :status AND b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
    Page<MissingPetBoard> findByStatusOrderByCreatedAtDesc(@Param("status") MissingPetStatus status, Pageable pageable);

    @RepositoryMethod("실종 제보: 홈 추천 후보 조회 (실종일 최신순)")
    @Query(value = "SELECT b FROM MissingPetBoard b JOIN FETCH b.user u "
            + "WHERE b.status = :status "
            + "AND b.isDeleted = false "
            + "AND u.isDeleted = false "
            + "AND u.status = 'ACTIVE' "
            + "ORDER BY b.lostDate DESC, b.createdAt DESC",
            countQuery = "SELECT COUNT(b) FROM MissingPetBoard b JOIN b.user u "
                    + "WHERE b.status = :status "
                    + "AND b.isDeleted = false "
                    + "AND u.isDeleted = false "
                    + "AND u.status = 'ACTIVE'")
    Page<MissingPetBoard> findHomeCandidatesByStatusOrderByLostDateDesc(
            @Param("status") MissingPetStatus status,
            Pageable pageable);

    @RepositoryMethod("실종 제보: 홈 추천 바운딩 박스 후보 조회")
    @Query(value = "SELECT b FROM MissingPetBoard b JOIN FETCH b.user u "
            + "WHERE b.status = :status "
            + "AND b.isDeleted = false "
            + "AND u.isDeleted = false "
            + "AND u.status = 'ACTIVE' "
            + "AND b.latitude IS NOT NULL "
            + "AND b.longitude IS NOT NULL "
            + "AND b.latitude BETWEEN :minLat AND :maxLat "
            + "AND b.longitude BETWEEN :minLng AND :maxLng "
            + "ORDER BY b.lostDate DESC, b.createdAt DESC",
            countQuery = "SELECT COUNT(b) FROM MissingPetBoard b JOIN b.user u "
                    + "WHERE b.status = :status "
                    + "AND b.isDeleted = false "
                    + "AND u.isDeleted = false "
                    + "AND u.status = 'ACTIVE' "
                    + "AND b.latitude IS NOT NULL "
                    + "AND b.longitude IS NOT NULL "
                    + "AND b.latitude BETWEEN :minLat AND :maxLat "
                    + "AND b.longitude BETWEEN :minLng AND :maxLng")
    Page<MissingPetBoard> findHomeCandidatesInBoundingBox(
            @Param("status") MissingPetStatus status,
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            Pageable pageable);

    @RepositoryMethod("실종 제보: 작성자 ID 조회 (경량)")
    @Query("SELECT b.user.idx FROM MissingPetBoard b WHERE b.idx = :idx AND b.isDeleted = false")
    Optional<Long> findUserIdByIdx(@Param("idx") Long idx);
}
