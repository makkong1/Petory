package com.linkup.Petory.domain.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.location.entity.LocationService;

import java.util.List;

@Repository
public interface LocationServiceRepository extends JpaRepository<LocationService, Long> {

        // 카테고리별 서비스 조회
        // List<LocationService> findByCategoryOrderByRatingDesc(String category);

        // 지역별 서비스 조회 (위도/경도 범위)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.latitude BETWEEN :minLat AND :maxLat AND " +
                        "ls.longitude BETWEEN :minLng AND :maxLng " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByLocationRange(@Param("minLat") Double minLat,
                        @Param("maxLat") Double maxLat,
                        @Param("minLng") Double minLng,
                        @Param("maxLng") Double maxLng);

        // 평점순 서비스 조회
        List<LocationService> findByOrderByRatingDesc();

        // 카테고리별 평점순 서비스 조회 (category3, category2, category1 순서로 검색)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:category IS NULL OR ls.category3 = :category OR ls.category2 = :category OR ls.category1 = :category) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByCategoryOrderByRatingDesc(@Param("category") String category);

        // 카테고리별 상위 10개 평점순 서비스 조회 (category3, category2, category1 순서로 검색)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:category IS NULL OR ls.category3 = :category OR ls.category2 = :category OR ls.category1 = :category) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findTop10ByCategoryOrderByRatingDesc(@Param("category") String category);

        // 이름으로 서비스 검색 (이름, 설명, 카테고리 포함)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.name LIKE CONCAT('%', :keyword, '%') " +
                        "OR ls.description LIKE CONCAT('%', :keyword, '%') " +
                        "OR ls.category1 LIKE CONCAT('%', :keyword, '%') " +
                        "OR ls.category2 LIKE CONCAT('%', :keyword, '%') " +
                        "OR ls.category3 LIKE CONCAT('%', :keyword, '%') " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByNameContaining(@Param("keyword") String keyword);

        // 특정 평점 이상의 서비스 조회
        List<LocationService> findByRatingGreaterThanEqualOrderByRatingDesc(Double minRating);

        // 이름과 주소로 중복 체크
        List<LocationService> findByNameAndAddress(String name, String address);

        boolean existsByNameAndAddress(String name, String address);

        // 주소로 중복 체크
        List<LocationService> findByAddress(String address);


        // 주소로 서비스 검색 (지역 검색) - 주소, 시도, 시군구 포함
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.address LIKE CONCAT('%', :address, '%') " +
                        "OR ls.sido LIKE CONCAT('%', :address, '%') " +
                        "OR ls.sigungu LIKE CONCAT('%', :address, '%') " +
                        "OR ls.eupmyeondong LIKE CONCAT('%', :address, '%') " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByAddressContaining(@Param("address") String address);

        // 반경 검색 (ST_Distance_Sphere 사용) - latitude, longitude 직접 사용
        // POINT 형식: POINT(경도 위도) = POINT(longitude latitude) 순서 사용 (MySQL 표준)
        // Native Query 파라미터 순서: ?1=latitude, ?2=longitude, ?3=radiusInMeters
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "latitude IS NOT NULL AND longitude IS NOT NULL AND " +
                        "ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 " +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters);

        // 서울 구/동 검색
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.address LIKE CONCAT('%서울%', :gu, '%') " +
                        "AND (:dong IS NULL OR ls.address LIKE CONCAT('%', :dong, '%')) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findBySeoulGuAndDong(@Param("gu") String gu, @Param("dong") String dong);

        // 전국 지역 검색 (시/도 > 시/군/구 > 동/면/리)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:sido IS NULL OR ls.address LIKE CONCAT('%', :sido, '%')) " +
                        "AND (:sigungu IS NULL OR ls.address LIKE CONCAT('%', :sigungu, '%')) " +
                        "AND (:dong IS NULL OR ls.address LIKE CONCAT('%', :dong, '%')) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByRegion(@Param("sido") String sido,
                        @Param("sigungu") String sigungu,
                        @Param("dong") String dong);

        // sigungu 필드로 직접 검색 (정확한 매칭)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.sigungu = :sigungu " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findBySigungu(@Param("sigungu") String sigungu);

        // 지역 계층별 검색
        // 시도별 조회
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.sido = :sido " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findBySido(@Param("sido") String sido);

        // 읍면동별 조회
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.eupmyeondong = :eupmyeondong " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByEupmyeondong(@Param("eupmyeondong") String eupmyeondong);

        // 도로명별 조회
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.roadName = :roadName " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByRoadName(@Param("roadName") String roadName);

        // 사용자 위치 기반 검색 (시군구/읍면동)
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "(:sigungu IS NULL OR ls.sigungu = :sigungu) AND " +
                        "(:eupmyeondong IS NULL OR ls.eupmyeondong = :eupmyeondong) " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByUserLocation(
                        @Param("sigungu") String sigungu,
                        @Param("eupmyeondong") String eupmyeondong);

        // 거리 순 정렬 반경 검색 (길찾기용)
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "latitude IS NOT NULL AND longitude IS NOT NULL AND " +
                        "ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 " +
                        "ORDER BY ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) ASC",
                        nativeQuery = true)
        List<LocationService> findByRadiusOrderByDistance(
                        @Param("latitude") Double latitude,
                        @Param("longitude") Double longitude,
                        @Param("radiusInMeters") Double radiusInMeters);
}
