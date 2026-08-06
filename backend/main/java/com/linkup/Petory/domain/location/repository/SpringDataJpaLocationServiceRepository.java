package com.linkup.Petory.domain.location.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaLocationServiceRepository extends JpaRepository<LocationService, Long> {

    @RepositoryMethod("장소 서비스: 평점순 전체 조회 (keyword·category 필터)")
    @Query(value = "SELECT * FROM locationservice WHERE "
            + "is_deleted = 0 "
            + "AND (:keyword IS NULL OR MATCH(name, description, category1, category2, category3) "
            + "     AGAINST(CONCAT(:keyword, '*') IN BOOLEAN MODE)) "
            + "AND (:category IS NULL "
            + "     OR category3 = :category "
            + "     OR category2 = :category "
            + "     OR category1 = :category) "
            + "ORDER BY rating DESC "
            + "LIMIT :limit", nativeQuery = true)
    List<LocationService> findByOrderByRatingDesc(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("limit") int limit);

    @RepositoryMethod("장소 서비스: 카테고리별 상위 10개 조회")
    @Query(value = "SELECT * FROM locationservice WHERE "
            + "(:category IS NULL "
            + " OR category3 = :category "
            + " OR category2 = :category "
            + " OR category1 = :category) AND "
            + "is_deleted = 0 "
            + "ORDER BY rating DESC "
            + "LIMIT 10", nativeQuery = true)
    List<LocationService> findTop10ByCategoryOrderByRatingDesc(@Param("category") String category);

    @RepositoryMethod("장소 서비스: 키워드 검색 (category 필터)")
    @Query(value = "SELECT * FROM locationservice "
            + "WHERE MATCH(name, description, category1, category2, category3) "
            + "AGAINST(CONCAT(:keyword, '*') IN BOOLEAN MODE) "
            + "AND is_deleted = 0 "
            + "AND (:category IS NULL "
            + "     OR category3 = :category "
            + "     OR category2 = :category "
            + "     OR category1 = :category) "
            + "ORDER BY rating DESC "
            + "LIMIT :limit", nativeQuery = true)
    List<LocationService> findByNameContaining(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("limit") int limit);

    @RepositoryMethod("장소 서비스: 이름+주소 존재 여부")
    @Query("SELECT COUNT(ls) > 0 FROM LocationService ls WHERE "
            + "ls.name = :name AND ls.address = :address AND "
            + "ls.isDeleted = false")
    boolean existsByNameAndAddress(@Param("name") String name, @Param("address") String address);

    @RepositoryMethod("장소 서비스: 이름+주소로 조회(업서트용)")
    @Query("SELECT ls FROM LocationService ls WHERE "
            + "ls.name = :name AND ls.address = :address AND "
            + "ls.isDeleted = false")
    List<LocationService> findByNameAndAddress(@Param("name") String name, @Param("address") String address);

    // spatial index를 실제로 잘 타고 있음
    // ST_Within + ST_Distance_Sphere 조합이 망하지 않음
    // LIKE '%??%'가 인덱스를 못 타더라도, 이미 반경 후보가 줄어든 뒤라 피해가 제한적임
    //
    // [IGNORE INDEX 이유] is_deleted 는 이 테이블에서 사실상 한 값뿐이라(카디널리티 1)
    // idx_locationservice_deleted_rating 은 반경 검색에서 단 한 행도 걸러내지 못한다.
    // 그런데 옵티마이저는 이걸 "싼 진입점"으로 오판해(cost 808 vs 공간 인덱스 2100)
    // 반경이 넓어지면 공간 인덱스 대신 이 인덱스로 전건 24,130 행을 읽었다.
    // 실측 (2026-08-02, 서울시청 중심, NearbySearchPolicy 상한, EXPLAIN ANALYZE 3회):
    //    5km / 10km  계획 동일 — 원래도 공간 인덱스라 변화 없음
    //   20km         24,130행 159ms → 공간 인덱스 6,826행 80ms   (2.0배)
    //   50km         24,130행 197ms → Table scan 24,130행 178ms  (사각형이 49%라 전건이 옳다)
    // FORCE INDEX(공간) 가 아니라 IGNORE 인 이유: "이 인덱스를 써라"는 데이터 분포가 바뀌면
    // 틀리지만, "아무것도 못 거르는 인덱스를 후보에서 빼라"는 분포와 무관하게 성립한다.
    // 인덱스 자체는 ORDER BY rating DESC 쿼리들이 쓰므로(COUNT_FETCH 530,860) 제거하지 않는다.
    @RepositoryMethod("장소 서비스: 반경 검색 (keyword·category 필터)")
    @Query(value = "SELECT * FROM locationservice ls IGNORE INDEX (idx_locationservice_deleted_rating) WHERE "
            + "ST_Within(ls.location, ST_GeomFromText("
            + "CONCAT('POLYGON((', "
            + ":latitude - (:radiusInMeters / 111000.0), ' ', :longitude - (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), ', ', "
            + ":latitude - (:radiusInMeters / 111000.0), ' ', :longitude + (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), ', ', "
            + ":latitude + (:radiusInMeters / 111000.0), ' ', :longitude + (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), ', ', "
            + ":latitude + (:radiusInMeters / 111000.0), ' ', :longitude - (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), ', ', "
            + ":latitude - (:radiusInMeters / 111000.0), ' ', :longitude - (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), '))'), "
            + "4326)) AND "
            + "ST_Distance_Sphere(ls.location, ST_GeomFromText("
            + "CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) <= :radiusInMeters AND "
            + "ls.is_deleted = 0 "
            + "AND (:keyword IS NULL OR ls.name LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:category IS NULL "
            + "     OR ls.category3 = :category "
            + "     OR ls.category2 = :category "
            + "     OR ls.category1 = :category) "
            + "ORDER BY "
            + "CASE WHEN :sort = 'score' THEN ls.score END DESC, "
            + "CASE WHEN :sort = 'stable' THEN ls.rating END DESC, "
            + "CASE WHEN :sort = 'stable' THEN ls.review_count END DESC, "
            + "CASE WHEN :sort = 'reviews' THEN ls.review_count END DESC, "
            + "CASE WHEN :sort = 'rating' THEN ls.rating END DESC, "
            + "CASE WHEN :sort NOT IN ('stable', 'score') THEN ST_Distance_Sphere(ls.location, ST_GeomFromText("
            + "CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) END ASC, "
            + "ls.rating DESC, ls.idx ASC "
            + "LIMIT :limit", nativeQuery = true)
    List<LocationService> findByRadius(@Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusInMeters") Double radiusInMeters,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("sort") String sort,
            @Param("limit") int limit);

    @RepositoryMethod("장소 서비스: 시군구별 조회 (keyword·category 필터)")
    @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_sigungu_deleted_rating) "
            + "WHERE sigungu = :sigungu AND "
            + "is_deleted = 0 "
            + "AND (:keyword IS NULL OR name LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:category IS NULL "
            + "     OR category3 = :category "
            + "     OR category2 = :category "
            + "     OR category1 = :category) "
            + "ORDER BY rating DESC "
            + "LIMIT :limit", nativeQuery = true)
    List<LocationService> findBySigungu(
            @Param("sigungu") String sigungu,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("limit") int limit);

    @RepositoryMethod("장소 서비스: 시도별 조회 (keyword·category 필터)")
    @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_sido_deleted_rating) "
            + "WHERE sido = :sido AND "
            + "is_deleted = 0 "
            + "AND (:keyword IS NULL OR name LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:category IS NULL "
            + "     OR category3 = :category "
            + "     OR category2 = :category "
            + "     OR category1 = :category) "
            + "ORDER BY rating DESC "
            + "LIMIT :limit", nativeQuery = true)
    List<LocationService> findBySido(
            @Param("sido") String sido,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("limit") int limit);

    // [FIX] 리뷰 평균을 DB에서 직접 계산해 rating 컬럼을 한 번의 UPDATE로 갱신.
    // 기존 read → AVG계산 → write 패턴은 동시 리뷰 시 Lost Update 위험이 있었음.
    // 인라인 뷰로 감싸 MySQL 버전 무관하게 호환성 확보.
    @RepositoryMethod("장소 서비스: 평점·리뷰수 직접 갱신 (원자적)")
    @Modifying
    @Query(value = "UPDATE locationservice SET "
            + "rating = ("
            + "SELECT avg_rating FROM ("
            + "SELECT COALESCE(AVG(r.rating), 0.0) AS avg_rating "
            + "FROM locationservicereview r JOIN users u ON u.idx = r.user_idx "
            + "WHERE r.service_idx = :serviceIdx AND r.is_deleted = 0 "
            + "AND u.is_deleted = 0 AND u.status <> 'BANNED'"
            + ") avg_stats"
            + "), "
            + "review_count = ("
            + "SELECT review_count FROM ("
            + "SELECT COUNT(*) AS review_count "
            + "FROM locationservicereview r JOIN users u ON u.idx = r.user_idx "
            + "WHERE r.service_idx = :serviceIdx AND r.is_deleted = 0 "
            + "AND u.is_deleted = 0 AND u.status <> 'BANNED'"
            + ") review_stats"
            + ") "
            + "WHERE idx = :serviceIdx", nativeQuery = true)
    void updateReviewStats(@Param("serviceIdx") Long serviceIdx);
}
