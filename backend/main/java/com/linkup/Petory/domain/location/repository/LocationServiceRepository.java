package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.location.entity.LocationService;

/**
 * LocationService 도메인 Repository 인터페이스입니다.
 *
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다. 다양한 데이터베이스 구현체(JPA,
 * MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 *
 * 구현체: - JpaLocationServiceAdapter: JPA 기반 구현체 - 다른 DB로 변경 시 새로운 어댑터를 만들고
 * @Primary를 옮기면 됩니다.
 */
public interface LocationServiceRepository {

    LocationService save(LocationService locationService);

    List<LocationService> saveAll(List<LocationService> locationServices);

    Optional<LocationService> findById(Long id);

    /**
     * 평점순 서비스 조회 (keyword·category 필터 포함)
     */
    List<LocationService> findByOrderByRatingDesc(String keyword, String category, int limit);

    /**
     * 카테고리별 상위 10개 평점순 서비스 조회
     */
    List<LocationService> findTop10ByCategoryOrderByRatingDesc(String category);

    /**
     * FULLTEXT 키워드 검색 (위치 없을 때 fallback, category 필터 포함)
     */
    List<LocationService> findByNameContaining(String keyword, String category, int limit);

    /**
     * 이름과 주소로 존재 여부 확인
     */
    boolean existsByNameAndAddress(String name, String address);

    /**
     * 반경 검색 (ST_Distance_Sphere 사용, keyword·category 필터 포함)
     */
    List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters,
            String keyword, String category, String sort, int limit);

    /**
     * 시군구별 조회 (keyword·category 필터 포함)
     */
    List<LocationService> findBySigungu(String sigungu, String keyword, String category, int limit);

    /**
     * 시도별 조회 (keyword·category 필터 포함)
     */
    List<LocationService> findBySido(String sido, String keyword, String category, int limit);

    /**
     * 읍면동별 조회 (keyword·category 필터 포함)
     */
    List<LocationService> findByEupmyeondong(String eupmyeondong, String keyword, String category, int limit);

    /**
     * 도로명별 조회 (keyword·category 필터 포함)
     */
    List<LocationService> findByRoadName(String roadName, String keyword, String category, int limit);

    /**
     * [FIX] 서비스 평점과 리뷰 수를 리뷰 집계 기준으로 원자적 갱신 (DB 단일 UPDATE)
     */
    void updateReviewStats(Long serviceIdx);
}
