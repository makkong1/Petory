package com.linkup.Petory.domain.location.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.location.entity.LocationService;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaLocationServiceRepository extends JpaRepository<LocationService, Long> {

        // 지역별 서비스 조회 (위도/경도 범위)
        // ✅ 개선: 공간 인덱스(idx_locationservice_location_spatial) 활용
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

        // 평점순 서비스 조회
        // ✅ 인덱스 활용: idx_locationservice_deleted_rating (is_deleted, rating DESC)
        // ⚠️ 주의: 전체 스캔이므로 페이징 필수 권장
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화)
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByOrderByRatingDesc();

        // 카테고리별 평점순 서비스 조회 (category3, category2, category1 순서로 검색)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:category IS NULL OR ls.category3 = :category OR ls.category2 = :category OR ls.category1 = :category) AND "
                        +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByCategoryOrderByRatingDesc(@Param("category") String category);

        // 카테고리별 상위 10개 평점순 서비스 조회 (category3, category2, category1 순서로 검색)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:category IS NULL OR ls.category3 = :category OR ls.category2 = :category OR ls.category1 = :category) AND "
                        +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findTop10ByCategoryOrderByRatingDesc(@Param("category") String category);

        @Query(value = "SELECT * FROM locationservice " +
                        "WHERE MATCH(name, description, category1, category2, category3) " +
                        "AGAINST(CONCAT(:keyword, '*') IN BOOLEAN MODE) " +
                        "AND is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByNameContaining(@Param("keyword") String keyword);

        // 특정 평점 이상의 서비스 조회
        // 💡 개선: rating IS NOT NULL 추가 및 is_deleted = 0 직접 사용 (인덱스 활용 최적화)
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "rating IS NOT NULL AND " +
                        "rating >= :minRating AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByRatingGreaterThanEqualOrderByRatingDesc(@Param("minRating") Double minRating);

        // 이름과 주소로 중복 체크
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.name = :name AND ls.address = :address AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false)")
        List<LocationService> findByNameAndAddress(@Param("name") String name, @Param("address") String address);

        @Query("SELECT COUNT(ls) > 0 FROM LocationService ls WHERE " +
                        "ls.name = :name AND ls.address = :address AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false)")
        boolean existsByNameAndAddress(@Param("name") String name, @Param("address") String address);

        // 주소로 중복 체크
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.address = :address AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false)")
        List<LocationService> findByAddress(@Param("address") String address);

        // 주소로 서비스 검색 (지역 검색) - 정확한 매칭 우선 사용
        // ⚠️ 성능 문제: LIKE '%keyword%' + OR 조건 4개 + filesort → 인덱스 불가
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

        // 개선안: 정확한 매칭 기반 검색 (인덱스 활용 가능)
        // 애플리케이션에서 sido/sigungu/eupmyeondong로 검색 시 이 메서드들을 사용
        // findBySido, findBySigungu, findByEupmyeondong 메서드들로 대체 가능

        // 반경 검색 (공간 인덱스 활용)
        // ✅ 개선: 공간 인덱스 1차 필터링 + ST_Distance_Sphere 2차 필터링
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

        // 서울 구/동 검색
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.address LIKE CONCAT('%서울%', :gu, '%') " +
                        "AND (:dong IS NULL OR ls.address LIKE CONCAT('%', :dong, '%')) AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findBySeoulGuAndDong(@Param("gu") String gu, @Param("dong") String dong);

        // 전국 지역 검색 (시/도 > 시/군/구 > 동/면/리)
        // ✅ 인덱스 활용 가능: 정확한 매칭 필드 사용 (sido, sigungu, eupmyeondong)
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

        // sigungu 필드로 직접 검색 (정확한 매칭)
        // ✅ 인덱스 활용: idx_locationservice_sigungu_deleted_rating (sigungu, is_deleted,
        // rating DESC)
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화)
        // 💡 인덱스 힌트: MySQL 옵티마이저가 올바른 인덱스를 선택하도록 보장
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_sigungu_deleted_rating) " +
                        "WHERE sigungu = :sigungu AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findBySigungu(@Param("sigungu") String sigungu);

        // 시도별 조회
        // ✅ 인덱스 활용: idx_locationservice_sido_deleted_rating (sido, is_deleted, rating
        // DESC)
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화, filesort 제거)
        @Query(value = "SELECT * FROM locationservice " +
                        "WHERE sido = :sido AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findBySido(@Param("sido") String sido);

        // 읍면동별 조회
        // ✅ 인덱스 활용: idx_locationservice_eupmyeondong_deleted_rating (eupmyeondong,
        // is_deleted, rating DESC)
        // 💡 개선: COALESCE 제거 → is_deleted = 0 직접 사용 (인덱스 활용 최적화)
        // 💡 인덱스 힌트: MySQL 옵티마이저가 올바른 인덱스를 선택하도록 보장
        @Query(value = "SELECT * FROM locationservice USE INDEX (idx_locationservice_eupmyeondong_deleted_rating) " +
                        "WHERE eupmyeondong = :eupmyeondong AND " +
                        "is_deleted = 0 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByEupmyeondong(@Param("eupmyeondong") String eupmyeondong);

        // 도로명별 조회
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.roadName = :roadName AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByRoadName(@Param("roadName") String roadName);

        // 사용자 위치 기반 검색 (시군구/읍면동)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:sigungu IS NULL OR ls.sigungu = :sigungu) AND " +
                        "(:eupmyeondong IS NULL OR ls.eupmyeondong = :eupmyeondong) AND " +
                        "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByUserLocation(
                        @Param("sigungu") String sigungu,
                        @Param("eupmyeondong") String eupmyeondong);

        // 거리 순 정렬 반경 검색 (길찾기용)
        // ✅ 개선: 공간 인덱스 1차 필터링 + ST_Distance_Sphere 2차 필터링 및 정렬
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
