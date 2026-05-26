package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

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
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "is_deleted = 0 " +
                        "AND (:keyword IS NULL OR MATCH(name, description, category1, category2, category3) " +
                        "     AGAINST(CONCAT(:keyword, '*') IN BOOLEAN MODE)) " +
                        "AND (:category IS NULL " +
                        "     OR category3 = :category " +
                        "     OR category2 = :category " +
                        "     OR category1 = :category) " +
                        "ORDER BY rating DESC " +
                        "LIMIT :limit", nativeQuery = true)
        List<LocationService> findByOrderByRatingDesc(
                        @Param("keyword") String keyword,
                        @Param("category") String category,
                        @Param("limit") int limit);

        @RepositoryMethod("장소 서비스: 카테고리별 상위 10개 조회")
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "(:category IS NULL " +
                        " OR category3 = :category " +
                        " OR category2 = :category " +
                        " OR category1 = :category) AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC " +
                        "LIMIT 10", nativeQuery = true)
        List<LocationService> findTop10ByCategoryOrderByRatingDesc(@Param("category") String category);

        @RepositoryMethod("장소 서비스: 키워드 검색 (category 필터)")
        @Query(value = "SELECT * FROM locationservice " +
                        "WHERE MATCH(name, description, category1, category2, category3) " +
                        "AGAINST(CONCAT(:keyword, '*') IN BOOLEAN MODE) " +
                        "AND is_deleted = 0 " +
                        "AND (:category IS NULL " +
                        "     OR category3 = :category " +
                        "     OR category2 = :category " +
                        "     OR category1 = :category) " +
                        "ORDER BY rating DESC " +
                        "LIMIT :limit", nativeQuery = true)
        List<LocationService> findByNameContaining(
                        @Param("keyword") String keyword,
                        @Param("category") String category,
                        @Param("limit") int limit);

        @RepositoryMethod("장소 서비스: 이름+주소 존재 여부")
        @Query("SELECT COUNT(ls) > 0 FROM LocationService ls WHERE " +
                        "ls.name = :name AND ls.address = :address AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false)")
        boolean existsByNameAndAddress(@Param("name") String name, @Param("address") String address);

        @RepositoryMethod("장소 서비스: name+address+dataSource 조회 (isDeleted 무관)")
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.name = :name AND ls.address = :address AND ls.dataSource = :dataSource")
        Optional<LocationService> findByNameAndAddressAndDataSource(
                        @Param("name") String name,
                        @Param("address") String address,
                        @Param("dataSource") String dataSource);

        // spatial index를 실제로 잘 타고 있음
        // ST_Within + ST_Distance_Sphere 조합이 망하지 않음
        // LIKE '%??%'가 인덱스를 못 타더라도, 이미 반경 후보가 줄어든 뒤라 피해가 제한적임
        @RepositoryMethod("장소 서비스: 반경 검색 (keyword·category 필터)")
        @Query(value = "SELECT * FROM locationservice ls WHERE " +
                        "ST_Within(ls.location, ST_GeomFromText(" +
                        "CONCAT('POLYGON((', " +
                        ":latitude - (:radiusInMeters / 111000.0), ' ', :longitude - (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), ', ', "
                        +
                        ":latitude - (:radiusInMeters / 111000.0), ' ', :longitude + (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), ', ', "
                        +
                        ":latitude + (:radiusInMeters / 111000.0), ' ', :longitude + (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), ', ', "
                        +
                        ":latitude + (:radiusInMeters / 111000.0), ' ', :longitude - (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), ', ', "
                        +
                        ":latitude - (:radiusInMeters / 111000.0), ' ', :longitude - (:radiusInMeters / (111000.0 * COS(RADIANS(:latitude)))), '))'), "
                        +
                        "4326)) AND " +
                        "ST_Distance_Sphere(ls.location, ST_GeomFromText(" +
                        "CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) <= :radiusInMeters AND " +
                        "ls.is_deleted = 0 " +
                        "AND (:keyword IS NULL OR ls.name LIKE CONCAT('%', :keyword, '%')) " +
                        "AND (:category IS NULL " +
                        "     OR ls.category3 = :category " +
                        "     OR ls.category2 = :category " +
                        "     OR ls.category1 = :category) " +
                        "ORDER BY " +
                        "CASE WHEN :sort = 'reviews' THEN ls.review_count END DESC, " +
                        "CASE WHEN :sort = 'rating' THEN ls.rating END DESC, " +
                        "ST_Distance_Sphere(ls.location, ST_GeomFromText(" +
                        "CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) ASC, " +
                        "ls.rating DESC, ls.idx ASC", nativeQuery = true)
        List<LocationService> findByRadius(@Param("latitude") Double latitude,
                        @Param("longitude") Double longitude,
                        @Param("radiusInMeters") Double radiusInMeters,
                        @Param("keyword") String keyword,
                        @Param("category") String category,
                        @Param("sort") String sort);

        @RepositoryMethod("장소 서비스: 시군구별 조회 (keyword·category 필터)")
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_sigungu_deleted_rating) " +
                        "WHERE sigungu = :sigungu AND " +
                        "is_deleted = 0 " +
                        "AND (:keyword IS NULL OR name LIKE CONCAT('%', :keyword, '%')) " +
                        "AND (:category IS NULL " +
                        "     OR category3 = :category " +
                        "     OR category2 = :category " +
                        "     OR category1 = :category) " +
                        "ORDER BY rating DESC " +
                        "LIMIT :limit", nativeQuery = true)
        List<LocationService> findBySigungu(
                        @Param("sigungu") String sigungu,
                        @Param("keyword") String keyword,
                        @Param("category") String category,
                        @Param("limit") int limit);

        @RepositoryMethod("장소 서비스: 시도별 조회 (keyword·category 필터)")
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_sido_deleted_rating) " +
                        "WHERE sido = :sido AND " +
                        "is_deleted = 0 " +
                        "AND (:keyword IS NULL OR name LIKE CONCAT('%', :keyword, '%')) " +
                        "AND (:category IS NULL " +
                        "     OR category3 = :category " +
                        "     OR category2 = :category " +
                        "     OR category1 = :category) " +
                        "ORDER BY rating DESC " +
                        "LIMIT :limit", nativeQuery = true)
        List<LocationService> findBySido(
                        @Param("sido") String sido,
                        @Param("keyword") String keyword,
                        @Param("category") String category,
                        @Param("limit") int limit);

        @RepositoryMethod("장소 서비스: 읍면동별 조회 (keyword·category 필터)")
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_eupmyeondong_deleted_rating) " +
                        "WHERE eupmyeondong = :eupmyeondong AND " +
                        "is_deleted = 0 " +
                        "AND (:keyword IS NULL OR name LIKE CONCAT('%', :keyword, '%')) " +
                        "AND (:category IS NULL " +
                        "     OR category3 = :category " +
                        "     OR category2 = :category " +
                        "     OR category1 = :category) " +
                        "ORDER BY rating DESC " +
                        "LIMIT :limit", nativeQuery = true)
        List<LocationService> findByEupmyeondong(
                        @Param("eupmyeondong") String eupmyeondong,
                        @Param("keyword") String keyword,
                        @Param("category") String category,
                        @Param("limit") int limit);

        @RepositoryMethod("장소 서비스: 도로명별 조회 (keyword·category 필터)")
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_road_name_deleted_rating) " +
                        "WHERE road_name = :roadName AND " +
                        "is_deleted = 0 " +
                        "AND (:keyword IS NULL OR name LIKE CONCAT('%', :keyword, '%')) " +
                        "AND (:category IS NULL " +
                        "     OR category3 = :category " +
                        "     OR category2 = :category " +
                        "     OR category1 = :category) " +
                        "ORDER BY rating DESC " +
                        "LIMIT :limit", nativeQuery = true)
        List<LocationService> findByRoadName(
                        @Param("roadName") String roadName,
                        @Param("keyword") String keyword,
                        @Param("category") String category,
                        @Param("limit") int limit);

        // [FIX] 리뷰 평균을 DB에서 직접 계산해 rating 컬럼을 한 번의 UPDATE로 갱신.
        // 기존 read → AVG계산 → write 패턴은 동시 리뷰 시 Lost Update 위험이 있었음.
        // 인라인 뷰로 감싸 MySQL 버전 무관하게 호환성 확보.
        @RepositoryMethod("장소 서비스: 평점·리뷰수 직접 갱신 (원자적)")
        @Modifying
        @Query(value = "UPDATE locationservice SET " +
                        "rating = (" +
                        "SELECT avg_rating FROM (" +
                        "SELECT COALESCE(AVG(r.rating), 0.0) AS avg_rating " +
                        "FROM locationservicereview r " +
                        "WHERE r.service_idx = :serviceIdx AND (r.is_deleted IS NULL OR r.is_deleted = 0)" +
                        ") avg_stats" +
                        "), " +
                        "review_count = (" +
                        "SELECT review_count FROM (" +
                        "SELECT COUNT(*) AS review_count " +
                        "FROM locationservicereview r " +
                        "WHERE r.service_idx = :serviceIdx AND (r.is_deleted IS NULL OR r.is_deleted = 0)" +
                        ") review_stats" +
                        ") " +
                        "WHERE idx = :serviceIdx", nativeQuery = true)
        void updateReviewStats(@Param("serviceIdx") Long serviceIdx);
}
