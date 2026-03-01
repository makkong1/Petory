package com.linkup.Petory.domain.location.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaLocationServiceRepository extends JpaRepository<LocationService, Long> {

        @RepositoryMethod("장소 서비스: 지역 범위별 조회")
        // ST_Within 사용하여 공간 인덱스로 빠른 범위 검색
        // ⚠️ 주의: ST_GeomFromText의 WKT 포맷은 (latitude, longitude) 순서 사용
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "ST_Within(location, ST_GeomFromText(" +
                        "CONCAT('POLYGON((', :minLat, ' ', :minLng, ', ', :minLat, ' ', :maxLng, ', ', " +
                        ":maxLat, ' ', :maxLng, ', ', :maxLat, ' ', :minLng, ', ', :minLat, ' ', :minLng, '))'), " +
                        "4326)) AND is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByLocationRange(@Param("minLat") Double minLat,
                        @Param("maxLat") Double maxLat,
                        @Param("minLng") Double minLng,
                        @Param("maxLng") Double maxLng);

        @RepositoryMethod("장소 서비스: 평점순 전체 조회")
        // ⚠️ 주의: 전체 스캔이므로 페이징 필수 권장
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화)
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByOrderByRatingDesc();

        @RepositoryMethod("장소 서비스: 카테고리별 평점순 조회")
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:category IS NULL OR ls.category3 = :category OR ls.category2 = :category OR ls.category1 = :category) AND "
                        +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByCategoryOrderByRatingDesc(@Param("category") String category);

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

        @RepositoryMethod("장소 서비스: 최소 평점 이상 조회")
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "rating IS NOT NULL AND " +
                        "rating >= :minRating AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByRatingGreaterThanEqualOrderByRatingDesc(@Param("minRating") Double minRating);

        @RepositoryMethod("장소 서비스: 이름+주소로 조회")
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.name = :name AND ls.address = :address AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false)")
        List<LocationService> findByNameAndAddress(@Param("name") String name, @Param("address") String address);

        @RepositoryMethod("장소 서비스: 이름+주소 존재 여부")
        @Query("SELECT COUNT(ls) > 0 FROM LocationService ls WHERE " +
                        "ls.name = :name AND ls.address = :address AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false)")
        boolean existsByNameAndAddress(@Param("name") String name, @Param("address") String address);

        @RepositoryMethod("장소 서비스: 주소로 조회")
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.address = :address AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false)")
        List<LocationService> findByAddress(@Param("address") String address);

        @RepositoryMethod("장소 서비스: 주소 포함 검색")
        // 개선: 정확한 매칭 필드(sido, sigungu, eupmyeondong)로 분리하여 애플리케이션에서 병합
        // 또는 FULLTEXT INDEX 사용 검토 (하지만 address는 도로명/지번 혼재로 한계 있음)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(ls.address LIKE CONCAT('%', :address, '%') " +
                        "OR ls.sido LIKE CONCAT('%', :address, '%') " +
                        "OR ls.sigungu LIKE CONCAT('%', :address, '%') " +
                        "OR ls.eupmyeondong LIKE CONCAT('%', :address, '%')) AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByAddressContaining(@Param("address") String address);

        @RepositoryMethod("장소 서비스: 반경 검색")
        // 1차: MBR 범위로 공간 인덱스 활용 (ST_Within) - 반경을 위도/경도 범위로 변환
        // 2차: 정확한 거리 계산 (ST_Distance_Sphere)
        // ⚠️ 주의: 반경을 위도/경도 범위로 변환 (대략 1도 ≈ 111km, 경도는 위도에 따라 조정)
        // ST_GeomFromText의 WKT 포맷은 (latitude, longitude) 순서 사용
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

        @RepositoryMethod("장소 서비스: 서울 구/동 검색")
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.address LIKE CONCAT('%서울%', :gu, '%') " +
                        "AND (:dong IS NULL OR ls.address LIKE CONCAT('%', :dong, '%')) AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findBySeoulGuAndDong(@Param("gu") String gu, @Param("dong") String dong);

        @RepositoryMethod("장소 서비스: 지역별 검색")
        // 복합 인덱스 활용을 위해 조건 우선순위: eupmyeondong > sigungu > sido
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화)
        // 💡 인덱스 힌트: MySQL 옵티마이저가 가장 구체적인 조건의 인덱스를 선택하도록 함
        @Query(value = "SELECT * FROM locationservice " +
                        "WHERE (:sido IS NULL OR sido = :sido) " +
                        "AND (:sigungu IS NULL OR sigungu = :sigungu) " +
                        "AND (:dong IS NULL OR eupmyeondong = :dong) " +
                        "AND is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByRegion(@Param("sido") String sido,
                        @Param("sigungu") String sigungu,
                        @Param("dong") String dong);

        @RepositoryMethod("장소 서비스: 시군구별 조회")
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화)
        // 💡 인덱스 힌트: MySQL 옵티마이저가 올바른 인덱스를 선택하도록 보장
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_sigungu_deleted_rating) " +
                        "WHERE sigungu = :sigungu AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findBySigungu(@Param("sigungu") String sigungu);

        @RepositoryMethod("장소 서비스: 시도별 조회")
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화, filesort 제거)
        @Query(value = "SELECT * FROM locationservice " +
                        "WHERE sido = :sido AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findBySido(@Param("sido") String sido);

        @RepositoryMethod("장소 서비스: 읍면동별 조회")
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화)
        // 💡 인덱스 힌트: MySQL 옵티마이저가 올바른 인덱스를 선택하도록 보장
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

        @RepositoryMethod("장소 서비스: 사용자 위치 기반 검색")
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:sigungu IS NULL OR ls.sigungu = :sigungu) AND " +
                        "(:eupmyeondong IS NULL OR ls.eupmyeondong = :eupmyeondong) AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByUserLocation(
                        @Param("sigungu") String sigungu,
                        @Param("eupmyeondong") String eupmyeondong);

        @RepositoryMethod("장소 서비스: 거리순 반경 검색")
        // 1차: MBR 범위로 공간 인덱스 활용 (ST_Within)
        // 2차: 정확한 거리 계산 및 정렬 (ST_Distance_Sphere)
        // ⚠️ 주의: 반경을 위도/경도 범위로 변환 (대략 1도 ≈ 111km)
        // ST_GeomFromText의 WKT 포맷은 (latitude, longitude) 순서 사용
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
                        "ORDER BY ST_Distance_Sphere(location, ST_GeomFromText(" +
                        "CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) ASC", nativeQuery = true)
        List<LocationService> findByRadiusOrderByDistance(
                        @Param("latitude") Double latitude,
                        @Param("longitude") Double longitude,
                        @Param("radiusInMeters") Double radiusInMeters);
}
