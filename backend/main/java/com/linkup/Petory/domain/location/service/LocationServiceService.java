package com.linkup.Petory.domain.location.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.location.converter.LocationServiceConverter;
import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.exception.LocationServiceAlreadyDeletedException;
import com.linkup.Petory.domain.location.exception.LocationServiceNotFoundException;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.petRecommendation.event.LocationSearchPerformedEvent;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceService {

    private static final int DEFAULT_RADIUS_METERS = 10_000;
    private static final int DEFAULT_RADIUS_LIMIT = 100;
    private static final String DEFAULT_RADIUS_SORT = "distance";

    private final LocationServiceConverter locationServiceConverter;
    private final LocationServiceRepository locationServiceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UsersRepository usersRepository;

    /**
     * 통합 검색 진입점 — 우선순위: 위치(반경) → 지역(sido/sigungu) → 키워드 FULLTEXT → 평점순
     */
    public List<LocationServiceDTO> searchLocationServices(
            String keyword, Double latitude, Double longitude, Integer radius,
            String sido, String sigungu, String category, String sort, Integer maxResults) {

        publishSearchEvent(keyword);
        keyword  = normalize(keyword);
        category = normalize(category);
        sido     = normalize(sido);
        sigungu  = normalize(sigungu);
        sort     = normalizeSort(sort);

        boolean hasLocation = latitude != null && longitude != null;
        boolean hasRegion   = StringUtils.hasText(sido) || StringUtils.hasText(sigungu);
        boolean hasKeyword  = StringUtils.hasText(keyword);
        boolean sortByScore = "score".equals(sort);

        List<LocationServiceDTO> results;
        if (hasLocation) {
            int radiusM = (radius != null && radius > 0) ? radius : DEFAULT_RADIUS_METERS;
            results = searchLocationServicesByLocation(latitude, longitude, radiusM, keyword, category, sort, maxResults);
        } else if (hasRegion) {
            results = searchLocationServicesByRegion(sido, sigungu, keyword, category, maxResults);
        } else if (hasKeyword) {
            results = searchLocationServicesByKeyword(keyword, category, maxResults);
        } else {
            results = searchLocationServicesByRegion(null, null, null, category, maxResults);
        }

        // score 정렬은 DB ORDER BY 미지원 — Java 후처리
        if (sortByScore) {
            results = new java.util.ArrayList<>(results);
            results.sort(Comparator.comparingDouble(
                    (LocationServiceDTO dto) -> dto.getScore() != null ? dto.getScore() : 0.0
            ).reversed());
        }
        return results;
    }

    @Cacheable(value = "popularLocationServices", key = "#p0")
    public List<LocationServiceDTO> getPopularLocationServices(String category) {
        return locationServiceRepository.findTop10ByCategoryOrderByRatingDesc(category)
                .stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());
    }

    /** 지역 계층별 검색: sigungu > sido > 전체 평점순 */
    public List<LocationServiceDTO> searchLocationServicesByRegion(
            String sido, String sigungu, String keyword, String category, Integer maxResults) {

        keyword  = normalize(keyword);
        category = normalize(category);

        int limit = (maxResults != null && maxResults > 0) ? maxResults : 50;
        long t0 = System.currentTimeMillis();

        List<LocationService> services;
        if (StringUtils.hasText(sigungu)) {
            services = locationServiceRepository.findBySigungu(sigungu, keyword, category, limit);
        } else if (StringUtils.hasText(sido)) {
            services = locationServiceRepository.findBySido(sido, keyword, category, limit);
        } else {
            services = locationServiceRepository.findByOrderByRatingDesc(keyword, category, limit);
        }

        List<LocationServiceDTO> result = services.stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());

        log.info("[지역검색] sido={} sigungu={} keyword={} → {}건 ({}ms)",
                sido, sigungu, keyword, result.size(), System.currentTimeMillis() - t0);
        return result;
    }

    /** 위치 기반 반경 검색 */
    public List<LocationServiceDTO> searchLocationServicesByLocation(
            Double latitude, Double longitude, Integer radiusInMeters,
            String keyword, String category, String sort, Integer maxResults) {

        keyword  = normalize(keyword);
        category = normalize(category);
        sort     = normalizeSort(sort);

        int limit = (maxResults != null && maxResults > 0) ? maxResults : DEFAULT_RADIUS_LIMIT;
        long t0 = System.currentTimeMillis();

        List<LocationService> services = locationServiceRepository
                .findByRadius(latitude, longitude, (double) radiusInMeters, keyword, category, sort, limit);

        List<LocationServiceDTO> result = services.stream()
                .map(service -> {
                    LocationServiceDTO dto = locationServiceConverter.toDTO(service);
                    if (service.getLatitude() != null && service.getLongitude() != null) {
                        dto.setDistance(calculateDistance(
                                latitude, longitude, service.getLatitude(), service.getLongitude()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("[반경검색] lat={} lng={} radius={}m keyword={} sort={} → {}건 ({}ms)",
                latitude, longitude, radiusInMeters, keyword, sort, result.size(), System.currentTimeMillis() - t0);
        return result;
    }

    /** FULLTEXT 키워드 검색 — 위치·지역 모두 없을 때 fallback */
    public List<LocationServiceDTO> searchLocationServicesByKeyword(
            String keyword, String category, Integer maxResults) {

        keyword  = normalize(keyword);
        category = normalize(category);

        int limit = (maxResults != null && maxResults > 0) ? maxResults : 50;
        long t0 = System.currentTimeMillis();

        List<LocationService> services = locationServiceRepository
                .findByNameContaining(keyword, category, limit);

        List<LocationServiceDTO> result = services.stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());

        log.info("[키워드검색] keyword={} category={} → {}건 ({}ms)",
                keyword, category, result.size(), System.currentTimeMillis() - t0);
        return result;
    }

    @Transactional
    public void deleteService(Long serviceIdx) {
        LocationService service = locationServiceRepository.findById(serviceIdx)
                .orElseThrow(LocationServiceNotFoundException::new);
        if (service.getIsDeleted() != null && service.getIsDeleted()) {
            throw new LocationServiceAlreadyDeletedException();
        }
        service.softDelete();
        locationServiceRepository.save(service);
        log.info("위치 서비스 삭제: serviceIdx={}", serviceIdx);
    }

    public Double calculateDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return null;
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void publishSearchEvent(String keyword) {
        if (!StringUtils.hasText(keyword)) return;
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) return;
            usersRepository.findActiveByIdString(auth.getName())
                    .map(user -> user.getIdx())
                    .ifPresent(userIdx ->
                            eventPublisher.publishEvent(
                                    new LocationSearchPerformedEvent(this, userIdx, keyword)));
        } catch (Exception ignored) {}
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeSort(String sort) {
        if (!StringUtils.hasText(sort)) return DEFAULT_RADIUS_SORT;
        return switch (sort.trim().toLowerCase()) {
            case "stable", "distance", "rating", "reviews", "score" -> sort.trim().toLowerCase();
            default -> DEFAULT_RADIUS_SORT;
        };
    }
}
