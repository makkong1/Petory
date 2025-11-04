package com.linkup.Petory.repository;

import com.linkup.Petory.entity.LocationService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

        // 카테고리별 평점순 서비스 조회
        List<LocationService> findByCategoryOrderByRatingDesc(String category);

        // 이름으로 서비스 검색
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.name LIKE %:keyword% OR ls.description LIKE %:keyword% " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByNameContaining(@Param("keyword") String keyword);

        // 특정 평점 이상의 서비스 조회
        List<LocationService> findByRatingGreaterThanEqualOrderByRatingDesc(Double minRating);

        // 이름과 주소로 중복 체크
        List<LocationService> findByNameAndAddress(String name, String address);

        // 주소로 중복 체크
        List<LocationService> findByAddress(String address);

        // 주소와 상세주소로 중복 체크
        List<LocationService> findByAddressAndDetailAddress(String address, String detailAddress);

        // 주소로 서비스 검색 (지역 검색) - 기본 검색
        @Query("SELECT ls FROM LocationService ls WHERE " +
                        "ls.address LIKE %:address% " +
                        "ORDER BY ls.rating DESC")
        List<LocationService> findByAddressContaining(@Param("address") String address);

        // 반경 검색 (ST_Distance_Sphere 사용) - 3km 이내
        // POINT 형식: 이 데이터베이스에서는 POINT(위도 경도) = POINT(latitude longitude) 순서 사용
        // ?1 = latitude, ?2 = longitude 이므로 POINT(?1 ?2) = POINT(위도 경도) 순서
        @Query(value = "SELECT * FROM locationservice WHERE " +
                        "ST_Distance_Sphere(coordinates, ST_GeomFromText(CONCAT('POINT(', ?1, ' ', ?2, ')'), 4326)) <= ?3 "
                        +
                        "ORDER BY rating DESC", nativeQuery = true)
        List<LocationService> findByRadius(@Param("latitude") Double latitude,
                        @Param("longitude") Double longitude,
                        @Param("radiusInMeters") Double radiusInMeters);

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
}
