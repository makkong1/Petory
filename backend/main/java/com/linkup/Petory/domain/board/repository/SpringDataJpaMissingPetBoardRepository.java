package com.linkup.Petory.domain.board.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    // 1단계: 바운딩 박스를 POLYGON 으로 만들어 SPATIAL 인덱스(geo_point)로 후보 idx 만 뽑는다.
    // 이전에는 latitude/longitude BETWEEN 이었는데 B-tree 로는 경도가 걸러지지 않아 풀스캔이었다
    // (carerequest V4 와 같은 병리). 좌표 순서는 POINT(위도 경도) — geo_point 저장 규약과 짝을 맞춘다.
    @Query(value = "SELECT b.idx FROM missing_pet_board b JOIN users u ON u.idx = b.user_idx "
            + "WHERE b.status = :status "
            + "AND b.is_deleted = false "
            + "AND u.is_deleted = false "
            + "AND u.status = 'ACTIVE' "
            + "AND ST_Within(b.geo_point, ST_GeomFromText(CONCAT('POLYGON((', "
            + ":minLat, ' ', :minLng, ', ', "
            + ":minLat, ' ', :maxLng, ', ', "
            + ":maxLat, ' ', :maxLng, ', ', "
            + ":maxLat, ' ', :minLng, ', ', "
            + ":minLat, ' ', :minLng, '))'), 4326)) "
            // [지도 반경검색 통일] 2차 정밀 반경 필터를 Java 에서 DB 로 내렸다.
            // 예전엔 DB 가 사각형 후보만 주고 서비스가 haversineKm 으로 원형 필터를 했는데,
            // 그러면 후보 :limit 건 중 사각형 모서리에 걸린 것들이 Java 에서 버려져
            // 실제 점수 계산 대상이 :limit 보다 적어졌다. 이제 DB 가 원형까지 걸러서
            // :limit 건이 전부 반경 안이고, 나머지 세 도메인과 필터 위치가 같아진다.
            // (점수 계산 0.6×최신성+0.4×근접도 와 최종 정렬은 그대로 서비스가 한다)
            + "AND ST_Distance_Sphere(b.geo_point, ST_GeomFromText("
            + "CONCAT('POINT(', :centerLat, ' ', :centerLng, ')'), 4326)) <= :radiusMeters "
            + "ORDER BY b.lost_date DESC, b.created_at DESC "
            + "LIMIT :limit", nativeQuery = true)
    List<Long> findHomeCandidateIdsInBoundingBox(
            @Param("status") String status,
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            @Param("centerLat") double centerLat,
            @Param("centerLng") double centerLng,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit);

    // 2단계: 뽑힌 idx 를 JOIN FETCH 로 재조회(작성자 N+1 방지). 최종 정렬·점수는 서비스가 다시 한다.
    @Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.idx IN :ids")
    List<MissingPetBoard> findByIdxInWithUser(@Param("ids") List<Long> ids);

    @RepositoryMethod("실종 제보: 홈 추천 바운딩 박스 후보 조회 (SPATIAL 2단계)")
    default Page<MissingPetBoard> findHomeCandidatesInBoundingBox(
            MissingPetStatus status,
            BigDecimal minLat,
            BigDecimal maxLat,
            BigDecimal minLng,
            BigDecimal maxLng,
            double centerLat,
            double centerLng,
            double radiusMeters,
            Pageable pageable) {
        List<Long> ids = findHomeCandidateIdsInBoundingBox(
                status.name(), minLat, maxLat, minLng, maxLng,
                centerLat, centerLng, radiusMeters, pageable.getPageSize());
        List<MissingPetBoard> content = ids.isEmpty() ? List.of() : findByIdxInWithUser(ids);
        return new PageImpl<>(content, pageable, content.size());
    }

    @RepositoryMethod("실종 제보: 작성자 ID 조회 (경량)")
    @Query("SELECT b.user.idx FROM MissingPetBoard b WHERE b.idx = :idx AND b.isDeleted = false")
    Optional<Long> findUserIdByIdx(@Param("idx") Long idx);
}
