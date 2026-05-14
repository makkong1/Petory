package com.linkup.Petory.domain.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.location.converter.LocationServiceConverter;
import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.exception.LocationServiceAlreadyDeletedException;
import com.linkup.Petory.domain.location.exception.LocationServiceNotFoundException;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceService {

    private static final int DEFAULT_RADIUS_METERS = 10_000;
    private static final String DEFAULT_RADIUS_SORT = "distance";

    private final LocationServiceConverter locationServiceConverter;
    private final LocationServiceRepository locationServiceRepository;

    /**
     * 주변 서비스 통합 검색 — B 방향(위치 우선, 키워드는 필터).
     *
     * <ol>
     *   <li>위치(lat·lng·radius) 있음 → 반경 검색 (keyword·category는 SQL WHERE 필터)</li>
     *   <li>지역(sido/sigungu/eupmyeondong/roadName) 있음 → 지역 검색 (keyword·category는 SQL WHERE 필터)</li>
     *   <li>keyword만 있음 → FULLTEXT 전국 검색 (위치 없을 때 fallback)</li>
     *   <li>아무것도 없음 → 전체 평점순</li>
     * </ol>
     */
    public List<LocationServiceDTO> searchLocationServices(
            String keyword,
            Double latitude,
            Double longitude,
            Integer radius,
            String sido,
            String sigungu,
            String eupmyeondong,
            String roadName,
            String category,
            String sort,
            Integer maxResults) {

        // 빈 문자열("")을 null로 정규화 — SQL의 :param IS NULL 조건이 올바르게 작동하도록
        keyword      = normalize(keyword);
        category     = normalize(category);
        sido         = normalize(sido);
        sigungu      = normalize(sigungu);
        eupmyeondong = normalize(eupmyeondong);
        roadName     = normalize(roadName);
        sort         = normalizeSort(sort);

        boolean hasLocation = latitude != null && longitude != null;
        boolean hasRegion = StringUtils.hasText(sido) || StringUtils.hasText(sigungu)
                || StringUtils.hasText(eupmyeondong) || StringUtils.hasText(roadName);
        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean sortByScore = "score".equals(sort);
        String dbSort = sortByScore ? "rating" : sort; // score는 DB 쿼리에서 rating으로 대체 후 post-sort

        List<LocationServiceDTO> results;

        // 1순위: 위치(반경) 우선
        if (hasLocation) {
            int radiusInMeters = (radius != null && radius > 0) ? radius : DEFAULT_RADIUS_METERS;
            results = searchLocationServicesByLocation(latitude, longitude, radiusInMeters,
                    keyword, category, dbSort, maxResults);
        } else if (hasRegion) {
            // 2순위: 지역 계층
            results = searchLocationServicesByRegion(sido, sigungu, eupmyeondong, roadName,
                    keyword, category, maxResults);
        } else if (hasKeyword) {
            // 3순위: 위치 없을 때 키워드 단독 FULLTEXT (fallback)
            results = searchLocationServicesByKeyword(keyword, category, maxResults);
        } else {
            // 4순위: 전체 평점순
            results = searchLocationServicesByRegion(null, null, null, null,
                    null, category, maxResults);
        }

        // score 정렬 post-processing
        if (sortByScore) {
            results = new java.util.ArrayList<>(results);
            results.sort(Comparator.comparingDouble(
                    (LocationServiceDTO dto) -> dto.getScore() != null ? dto.getScore() : 0.0
            ).reversed());
        }

        return results;
    }

    /**
     * 인기 위치 서비스 조회 (카테고리별 상위 10개)
     */
    @Cacheable(value = "popularLocationServices", key = "#category")
    public List<LocationServiceDTO> getPopularLocationServices(String category) {
        return locationServiceRepository.findTop10ByCategoryOrderByRatingDesc(category)
                .stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 지역 계층별 서비스 조회
     * 우선순위: roadName > eupmyeondong > sigungu > sido > 전체
     * keyword·category 필터는 SQL WHERE에서 처리
     */
    public List<LocationServiceDTO> searchLocationServicesByRegion(
            String sido,
            String sigungu,
            String eupmyeondong,
            String roadName,
            String keyword,
            String category,
            Integer maxResults) {

        keyword  = normalize(keyword);
        category = normalize(category);

        long methodStartTime = System.currentTimeMillis();

        List<LocationService> services;

        // 지역 계층 우선순위에 따라 조회 (keyword·category는 쿼리 내부에서 필터)
        long queryStartTime = System.currentTimeMillis();
        if (StringUtils.hasText(roadName)) {
            services = locationServiceRepository.findByRoadName(roadName, keyword, category);
            log.debug("도로명 검색: roadName={}, keyword={}, category={}, 결과={}개",
                    roadName, keyword, category, services.size());
        } else if (StringUtils.hasText(eupmyeondong)) {
            services = locationServiceRepository.findByEupmyeondong(eupmyeondong, keyword, category);
            log.debug("읍면동 검색: eupmyeondong={}, keyword={}, category={}, 결과={}개",
                    eupmyeondong, keyword, category, services.size());
        } else if (StringUtils.hasText(sigungu)) {
            services = locationServiceRepository.findBySigungu(sigungu, keyword, category);
            log.debug("시군구 검색: sigungu={}, keyword={}, category={}, 결과={}개",
                    sigungu, keyword, category, services.size());
        } else if (StringUtils.hasText(sido)) {
            services = locationServiceRepository.findBySido(sido, keyword, category);
            log.debug("시도 검색: sido={}, keyword={}, category={}, 결과={}개",
                    sido, keyword, category, services.size());
        } else {
            services = locationServiceRepository.findByOrderByRatingDesc(keyword, category);
            log.debug("전체 조회: keyword={}, category={}, 결과={}개",
                    keyword, category, services.size());
        }
        long queryTime = System.currentTimeMillis() - queryStartTime;
        log.info("[성능 측정] DB 쿼리 실행 시간: {}ms, 조회된 레코드 수: {}개", queryTime, services.size());

        // 최대 결과 수 제한
        if (maxResults != null && maxResults > 0) {
            services = services.stream()
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        // DTO 변환
        long dtoConvertStartTime = System.currentTimeMillis();
        List<LocationServiceDTO> result = services.stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());
        long dtoConvertTime = System.currentTimeMillis() - dtoConvertStartTime;

        long totalTime = System.currentTimeMillis() - methodStartTime;
        log.info("[성능 측정] searchLocationServicesByRegion 전체: {}ms (쿼리: {}ms, DTO변환: {}ms)",
                totalTime, queryTime, dtoConvertTime);

        return result;
    }

    /**
     * 위치 기반 서비스 조회 (반경 검색)
     * keyword·category 필터는 SQL WHERE에서 처리
     */
    public List<LocationServiceDTO> searchLocationServicesByLocation(
            Double latitude,
            Double longitude,
            Integer radiusInMeters,
            String keyword,
            String category,
            String sort,
            Integer maxResults) {

        keyword  = normalize(keyword);
        category = normalize(category);
        sort     = normalizeSort(sort);

        long methodStartTime = System.currentTimeMillis();
        log.info("[위치 기반 검색] 시작 - lat={}, lng={}, radius={}m, keyword={}, category={}",
                latitude, longitude, radiusInMeters, keyword, category);

        // 반경 검색 (keyword·category 포함)
        long queryStartTime = System.currentTimeMillis();
        List<LocationService> services = locationServiceRepository
                .findByRadius(latitude, longitude, (double) radiusInMeters, keyword, category, sort);
        long queryTime = System.currentTimeMillis() - queryStartTime;
        log.info("[성능 측정] 위치 기반 DB 쿼리 실행 시간: {}ms, 조회된 레코드 수: {}개", queryTime, services.size());

        // 최대 결과 수 제한
        if (maxResults != null && maxResults > 0) {
            services = services.stream()
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        // DTO 변환 및 거리 정보 설정
        long dtoConvertStartTime = System.currentTimeMillis();
        List<LocationServiceDTO> result = services.stream()
                .map(service -> {
                    LocationServiceDTO dto = locationServiceConverter.toDTO(service);
                    if (service.getLatitude() != null && service.getLongitude() != null) {
                        Double distance = calculateDistance(
                                latitude, longitude,
                                service.getLatitude(), service.getLongitude());
                        dto.setDistance(distance);
                    }
                    return dto;
                })
                .collect(Collectors.toList());
        long dtoConvertTime = System.currentTimeMillis() - dtoConvertStartTime;

        long totalTime = System.currentTimeMillis() - methodStartTime;
        log.info("[성능 측정] searchLocationServicesByLocation 전체: {}ms (쿼리: {}ms, DTO변환: {}ms)",
                totalTime, queryTime, dtoConvertTime);

        return result;
    }

    /**
     * FULLTEXT 키워드 검색 — 위치 정보가 없을 때만 사용 (fallback)
     * category 필터는 SQL WHERE에서 처리
     */
    public List<LocationServiceDTO> searchLocationServicesByKeyword(
            String keyword,
            String category,
            Integer maxResults) {

        keyword  = normalize(keyword);
        category = normalize(category);

        long methodStartTime = System.currentTimeMillis();

        long queryStartTime = System.currentTimeMillis();
        List<LocationService> services = locationServiceRepository.findByNameContaining(keyword, category);
        long queryTime = System.currentTimeMillis() - queryStartTime;
        log.info("[성능 측정] 키워드 검색 DB 쿼리 실행 시간: {}ms, 조회된 레코드 수: {}개", queryTime, services.size());

        // 최대 결과 수 제한
        if (maxResults != null && maxResults > 0) {
            services = services.stream()
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        // DTO 변환
        long dtoConvertStartTime = System.currentTimeMillis();
        List<LocationServiceDTO> result = services.stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());
        long dtoConvertTime = System.currentTimeMillis() - dtoConvertStartTime;

        long totalTime = System.currentTimeMillis() - methodStartTime;
        log.info("[성능 측정] searchLocationServicesByKeyword 전체: {}ms (쿼리: {}ms, DTO변환: {}ms)",
                totalTime, queryTime, dtoConvertTime);

        return result;
    }

    /**
     * 빈 문자열("")을 null로 정규화하고 앞뒤 공백을 제거한다.
     * SQL의 {@code :param IS NULL} 조건이 올바르게 작동하려면 빈 문자열이 아닌 null이 전달되어야 한다.
     */
    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return DEFAULT_RADIUS_SORT;
        }
        return switch (sort.trim().toLowerCase()) {
            case "distance", "rating", "reviews", "score" -> sort.trim().toLowerCase();
            default -> DEFAULT_RADIUS_SORT;
        };
    }

    /**
     * 두 좌표 간 거리 계산 (Haversine 공식, 미터 단위)
     */
    public Double calculateDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }

        final int R = 6371000; // 지구 반경 (미터)

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * 위치 서비스 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteService(Long serviceIdx) {
        LocationService service = locationServiceRepository.findById(serviceIdx)
                .orElseThrow(LocationServiceNotFoundException::new);

        if (service.getIsDeleted() != null && service.getIsDeleted()) {
            throw new LocationServiceAlreadyDeletedException();
        }

        service.setIsDeleted(true);
        service.setDeletedAt(java.time.LocalDateTime.now());
        locationServiceRepository.save(service);

        log.info("위치 서비스가 삭제되었습니다. serviceIdx: {}", serviceIdx);
    }
}
