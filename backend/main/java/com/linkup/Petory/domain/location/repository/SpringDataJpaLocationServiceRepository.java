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

        @RepositoryMethod("장소 서비스: 평점순 전체 조회")
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByOrderByRatingDesc();

        @RepositoryMethod("장소 서비스: 카테고리별 상위 10개 조회")
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:category IS NULL OR ls.category3 = :category OR ls.category2 = :category OR ls.category1 = :category) AND "
                        +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findTop10ByCategoryOrderByRatingDesc(@Param("category") String category);

        @RepositoryMethod("장소 서비스: 키워드 검색")
        @Query(value = "SELECT * FROM locationservice " +
                        "WHERE MATCH(name, description, category1, category2, category3) " +
                        "AGAINST(CONCAT(:keyword, '*') IN BOOLEAN MODE) " +
                        "AND is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByNameContaining(@Param("keyword") String keyword);

        @RepositoryMethod("장소 서비스: 이름+주소 존재 여부")
        @Query("SELECT COUNT(ls) > 0 FROM LocationService ls WHERE " +
                        "ls.name = :name AND ls.address = :address AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false)")
        boolean existsByNameAndAddress(@Param("name") String name, @Param("address") String address);

        @RepositoryMethod("장소 서비스: 반경 검색")
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "ST_Within(location, ST_GeomFromText(" +
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
                        "ST_Distance_Sphere(location, ST_GeomFromText(" +
                        "CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) <= :radiusInMeters AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByRadius(@Param("latitude") Double latitude,
                        @Param("longitude") Double longitude,
                        @Param("radiusInMeters") Double radiusInMeters);

        @RepositoryMethod("장소 서비스: 시군구별 조회")
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_sigungu_deleted_rating) " +
                        "WHERE sigungu = :sigungu AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findBySigungu(@Param("sigungu") String sigungu);

        @RepositoryMethod("장소 서비스: 시도별 조회")
        @Query(value = "SELECT * FROM locationservice " +
                        "WHERE sido = :sido AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findBySido(@Param("sido") String sido);

        @RepositoryMethod("장소 서비스: 읍면동별 조회")
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_eupmyeondong_deleted_rating) " +
                        "WHERE eupmyeondong = :eupmyeondong AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByEupmyeondong(@Param("eupmyeondong") String eupmyeondong);

        @RepositoryMethod("장소 서비스: 도로명별 조회")
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.roadName = :roadName AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByRoadName(@Param("roadName") String roadName);

        // [FIX] 리뷰 평균을 DB에서 직접 계산해 rating 컬럼을 한 번의 UPDATE로 갱신.
        // 기존 read → AVG계산 → write 패턴은 동시 리뷰 시 Lost Update 위험이 있었음.
        // 인라인 뷰로 감싸 MySQL 버전 무관하게 호환성 확보.
        @RepositoryMethod("장소 서비스: 평점 직접 갱신 (원자적)")
        @Modifying
        @Query(value = "UPDATE locationservice SET rating = (" +
                        "SELECT avg_rating FROM (" +
                        "SELECT COALESCE(AVG(r.rating), 0.0) AS avg_rating " +
                        "FROM locationservicereview r " +
                        "WHERE r.service_idx = :serviceIdx AND (r.is_deleted IS NULL OR r.is_deleted = 0)" +
                        ") t" +
                        ") WHERE idx = :serviceIdx", nativeQuery = true)
        void updateRatingByAvg(@Param("serviceIdx") Long serviceIdx);
}
