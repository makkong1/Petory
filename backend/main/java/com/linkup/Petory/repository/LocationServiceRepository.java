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
}
