package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.location.entity.LocationService;

/**
 * LocationService 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaLocationServiceAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface LocationServiceRepository {

    // 기본 CRUD 메서드
    LocationService save(LocationService locationService);

    List<LocationService> saveAll(List<LocationService> locationServices);

    Optional<LocationService> findById(Long id);

    void delete(LocationService locationService);

    void deleteById(Long id);

    /**
     * 지역별 서비스 조회 (위도/경도 범위)
     */
    List<LocationService> findByLocationRange(
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng);

    /**
     * 평점순 서비스 조회
     */
    List<LocationService> findByOrderByRatingDesc();

    /**
     * 카테고리별 평점순 서비스 조회 (category3, category2, category1 순서로 검색)
     */
    List<LocationService> findByCategoryOrderByRatingDesc(String category);

    /**
     * 카테고리별 상위 10개 평점순 서비스 조회
     */
    List<LocationService> findTop10ByCategoryOrderByRatingDesc(String category);

    /**
     * 이름으로 서비스 검색 (이름, 설명, 카테고리 포함)
     */
    List<LocationService> findByNameContaining(String keyword);

    /**
     * 특정 평점 이상의 서비스 조회
     */
    List<LocationService> findByRatingGreaterThanEqualOrderByRatingDesc(Double minRating);

    /**
     * 이름과 주소로 중복 체크
     */
    List<LocationService> findByNameAndAddress(String name, String address);

    /**
     * 이름과 주소로 존재 여부 확인
     */
    boolean existsByNameAndAddress(String name, String address);

    /**
     * 주소로 중복 체크
     */
    List<LocationService> findByAddress(String address);

    /**
     * 주소로 서비스 검색 (지역 검색) - 주소, 시도, 시군구 포함
     */
    List<LocationService> findByAddressContaining(String address);

    /**
     * 반경 검색 (ST_Distance_Sphere 사용)
     */
    List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters);

    /**
     * 서울 구/동 검색
     */
    List<LocationService> findBySeoulGuAndDong(String gu, String dong);

    /**
     * 전국 지역 검색 (시/도 > 시/군/구 > 동/면/리)
     */
    List<LocationService> findByRegion(String sido, String sigungu, String dong);

    /**
     * sigungu 필드로 직접 검색 (정확한 매칭)
     */
    List<LocationService> findBySigungu(String sigungu);

    /**
     * 시도별 조회
     */
    List<LocationService> findBySido(String sido);

    /**
     * 읍면동별 조회
     */
    List<LocationService> findByEupmyeondong(String eupmyeondong);

    /**
     * 도로명별 조회
     */
    List<LocationService> findByRoadName(String roadName);

    /**
     * 사용자 위치 기반 검색 (시군구/읍면동)
     */
    List<LocationService> findByUserLocation(String sigungu, String eupmyeondong);

    /**
     * 거리 순 정렬 반경 검색 (길찾기용)
     */
    List<LocationService> findByRadiusOrderByDistance(
            Double latitude,
            Double longitude,
            Double radiusInMeters);
}
