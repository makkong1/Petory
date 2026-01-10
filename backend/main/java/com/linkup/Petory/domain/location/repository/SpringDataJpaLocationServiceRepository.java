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
    // ⚠️ 성능 문제: ORDER BY rating DESC → filesort 발생 (is_deleted 조건으로 복합 인덱스 활용 불가)
    // 개선: (is_deleted, rating DESC) 복합 인덱스 필요 또는 애플리케이션에서 정렬
    @Query("SELECT ls FROM LocationService ls WHERE " +
            "ls.latitude BETWEEN :minLat AND :maxLat AND " +
            "ls.longitude BETWEEN :minLng AND :maxLng AND " +
            "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
            "ORDER BY ls.rating DESC")
    List<LocationService> findByLocationRange(@Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);

    // 평점순 서비스 조회
    // ✅ 인덱스 활용: idx_locationservice_deleted_rating (is_deleted, rating DESC)
    // ⚠️ 주의: 전체 스캔이므로 페이징 필수 권장
    @Query(value = "SELECT * FROM locationservice WHERE " +
            "(COALESCE(is_deleted, 0) = 0) " +
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

    // 이름으로 서비스 검색 (이름, 설명, 카테고리 포함)
    // ⚠️ 성능 문제: LIKE '%keyword%' + OR 조건 5개 + filesort → 인덱스 불가
    // 개선: FULLTEXT INDEX 활용 (MATCH ... AGAINST) 또는 Elasticsearch 도입 검토
    @Query("SELECT ls FROM LocationService ls WHERE " +
            "(ls.name LIKE CONCAT('%', :keyword, '%') " +
            "OR ls.description LIKE CONCAT('%', :keyword, '%') " +
            "OR ls.category1 LIKE CONCAT('%', :keyword, '%') " +
            "OR ls.category2 LIKE CONCAT('%', :keyword, '%') " +
            "OR ls.category3 LIKE CONCAT('%', :keyword, '%')) AND " +
            "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
            "ORDER BY ls.rating DESC")
    List<LocationService> findByNameContaining(@Param("keyword") String keyword);

    // 개선안: FULLTEXT INDEX 사용 (nativeQuery)
    // @Query(value = "SELECT * FROM locationservice WHERE " +
    // "(MATCH(name, description) AGAINST(:keyword IN BOOLEAN MODE) " +
    // "OR category1 = :keyword OR category2 = :keyword OR category3 = :keyword) AND
    // " +
    // "(is_deleted IS NULL OR is_deleted = 0) " +
    // "ORDER BY rating DESC", nativeQuery = true)
    // List<LocationService> findByNameContainingFullText(@Param("keyword") String
    // keyword);

    // 특정 평점 이상의 서비스 조회
    @Query("SELECT ls FROM LocationService ls WHERE " +
            "ls.rating >= :minRating AND " +
            "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
            "ORDER BY ls.rating DESC")
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

    // 반경 검색 (ST_Distance_Sphere 사용) - latitude, longitude 직접 사용
    // ⚠️ 성능 문제: ST_Distance_Sphere는 함수이므로 인덱스 불가 + filesort
    // 개선: 공간 인덱스(Spatial Index) 또는 좌표 범위로 1차 필터링 후 거리 계산
    @Query(value = "SELECT * FROM locationservice WHERE " +
            "latitude IS NOT NULL AND longitude IS NOT NULL AND " +
            "ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 AND " +
            "(is_deleted IS NULL OR is_deleted = 0) " +
            "ORDER BY rating DESC", nativeQuery = true)
    List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters);

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
    @Query(value = "SELECT * FROM locationservice WHERE " +
            "(:sido IS NULL OR sido = :sido) " +
            "AND (:sigungu IS NULL OR sigungu = :sigungu) " +
            "AND (:dong IS NULL OR eupmyeondong = :dong) AND " +
            "(COALESCE(is_deleted, 0) = 0) " +
            "ORDER BY rating DESC", nativeQuery = true)
    List<LocationService> findByRegion(@Param("sido") String sido,
            @Param("sigungu") String sigungu,
            @Param("dong") String dong);

    // 구버전 (LIKE 사용, 성능 문제 있음 - 하위 호환성 위해 유지)
    // @Deprecated
    // @Query("SELECT ls FROM LocationService ls WHERE " +
    // "(:sido IS NULL OR ls.address LIKE CONCAT('%', :sido, '%')) " +
    // "AND (:sigungu IS NULL OR ls.address LIKE CONCAT('%', :sigungu, '%')) " +
    // "AND (:dong IS NULL OR ls.address LIKE CONCAT('%', :dong, '%')) AND " +
    // "(ls.isDeleted IS NULL OR ls.isDeleted = false) " +
    // "ORDER BY ls.rating DESC")
    // List<LocationService> findByRegionOld(@Param("sido") String sido,
    // @Param("sigungu") String sigungu,
    // @Param("dong") String dong);

    // sigungu 필드로 직접 검색 (정확한 매칭)
    // ✅ 인덱스 활용: idx_locationservice_sigungu (sigungu, rating DESC) +
    // idx_locationservice_deleted_rating
    @Query(value = "SELECT * FROM locationservice WHERE " +
            "sigungu = :sigungu AND " +
            "(COALESCE(is_deleted, 0) = 0) " +
            "ORDER BY rating DESC", nativeQuery = true)
    List<LocationService> findBySigungu(@Param("sigungu") String sigungu);

    // 시도별 조회
    // ✅ 인덱스 활용: idx_locationservice_sido (sido, rating DESC) +
    // idx_locationservice_deleted_rating
    // ⚠️ 주의: is_deleted 조건 최적화를 위해 Native Query 사용 권장
    @Query(value = "SELECT * FROM locationservice WHERE " +
            "sido = :sido AND " +
            "(COALESCE(is_deleted, 0) = 0) " +
            "ORDER BY rating DESC", nativeQuery = true)
    List<LocationService> findBySido(@Param("sido") String sido);

    // 읍면동별 조회
    // ✅ 인덱스 활용: idx_locationservice_eupmyeondong (eupmyeondong, rating DESC) +
    // idx_locationservice_deleted_rating
    @Query(value = "SELECT * FROM locationservice WHERE " +
            "eupmyeondong = :eupmyeondong AND " +
            "(COALESCE(is_deleted, 0) = 0) " +
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
    // ⚠️ 성능 문제: ST_Distance_Sphere 함수 2회 호출 + filesort (대용량에서 매우 위험)
    // 개선: 좌표 범위로 1차 필터링 → 애플리케이션에서 거리 계산 및 정렬 또는 공간 인덱스
    @Query(value = "SELECT * FROM locationservice WHERE " +
            "latitude IS NOT NULL AND longitude IS NOT NULL AND " +
            "ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 AND " +
            "(is_deleted IS NULL OR is_deleted = 0) " +
            "ORDER BY ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) ASC", nativeQuery = true)
    List<LocationService> findByRadiusOrderByDistance(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusInMeters") Double radiusInMeters);
}
