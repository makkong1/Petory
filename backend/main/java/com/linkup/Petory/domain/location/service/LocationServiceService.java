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
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceService {

    private final LocationServiceConverter locationServiceConverter;
    private final LocationServiceRepository locationServiceRepository;

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
     * 
     * @param sido         시도 (선택, 예: "서울특별시", "경기도")
     * @param sigungu      시군구 (선택, 예: "노원구", "고양시 덕양구")
     * @param eupmyeondong 읍면동 (선택, 예: "상계동", "동산동")
     * @param roadName     도로명 (선택, 예: "상계로", "동세로")
     * @param category     카테고리 (선택, 예: "동물약국", "미술관")
     * @param maxResults   최대 결과 수 (선택)
     * @return 검색 결과
     */
    public List<LocationServiceDTO> searchLocationServicesByRegion(
            String sido,
            String sigungu,
            String eupmyeondong,
            String roadName,
            String category,
            Integer maxResults) {

        long methodStartTime = System.currentTimeMillis();

        List<LocationService> services;

        // 지역 계층 우선순위에 따라 조회
        long queryStartTime = System.currentTimeMillis();
        if (StringUtils.hasText(roadName)) {
            services = locationServiceRepository.findByRoadName(roadName);
            log.debug("도로명 검색: roadName={}, 결과={}개", roadName, services.size());
        } else if (StringUtils.hasText(eupmyeondong)) {
            services = locationServiceRepository.findByEupmyeondong(eupmyeondong);
            log.debug("읍면동 검색: eupmyeondong={}, 결과={}개", eupmyeondong, services.size());
        } else if (StringUtils.hasText(sigungu)) {
            services = locationServiceRepository.findBySigungu(sigungu);
            log.debug("시군구 검색: sigungu={}, 결과={}개", sigungu, services.size());
        } else if (StringUtils.hasText(sido)) {
            services = locationServiceRepository.findBySido(sido);
            log.debug("시도 검색: sido={}, 결과={}개", sido, services.size());
        } else {
            // 모든 파라미터가 없으면 전체 조회
            services = locationServiceRepository.findByOrderByRatingDesc();
            log.debug("전체 조회: 결과={}개", services.size());
        }
        long queryTime = System.currentTimeMillis() - queryStartTime;
        log.info("⏱️  [성능 측정] DB 쿼리 실행 시간: {}ms, 조회된 레코드 수: {}개", queryTime, services.size());

        // 카테고리 필터링
        long filterStartTime = System.currentTimeMillis();
        long filterTime = 0;
        if (StringUtils.hasText(category) && !services.isEmpty()) {
            String categoryLower = category.toLowerCase(Locale.ROOT).trim();
            services = services.stream()
                    .filter(service -> {
                        // category3 우선 확인
                        if (service.getCategory3() != null) {
                            String cat3 = service.getCategory3().toLowerCase(Locale.ROOT).trim();
                            if (cat3.equals(categoryLower)) {
                                return true;
                            }
                        }
                        // category2 확인
                        if (service.getCategory2() != null) {
                            String cat2 = service.getCategory2().toLowerCase(Locale.ROOT).trim();
                            if (cat2.equals(categoryLower)) {
                                return true;
                            }
                        }
                        // category1 확인
                        if (service.getCategory1() != null) {
                            String cat1 = service.getCategory1().toLowerCase(Locale.ROOT).trim();
                            if (cat1.equals(categoryLower)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            filterTime = System.currentTimeMillis() - filterStartTime;
            log.info("⏱️  [성능 측정] 카테고리 필터링 시간: {}ms, 필터링 후 결과 수: {}개", filterTime, services.size());
        }

        // 최대 결과 수 제한 (null이거나 0이면 제한 없음)
        if (maxResults != null && maxResults > 0) {
            services = services.stream()
                    .limit(maxResults)
                    .collect(Collectors.toList());
            log.debug("결과 수 제한: maxResults={}, 제한 후={}개", maxResults, services.size());
        } else {
            log.debug("결과 수 제한 없음: 전체={}개", services.size());
        }

        // DTO로 변환
        long dtoConvertStartTime = System.currentTimeMillis();
        List<LocationServiceDTO> result = services.stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());
        long dtoConvertTime = System.currentTimeMillis() - dtoConvertStartTime;
        log.info("⏱️  [성능 측정] DTO 변환 시간: {}ms, 변환된 레코드 수: {}개", dtoConvertTime, result.size());

        long totalTime = System.currentTimeMillis() - methodStartTime;
        log.info("✅ [성능 측정] searchLocationServicesByRegion 전체 시간: {}ms (쿼리: {}ms, 필터링: {}ms, DTO변환: {}ms)",
                totalTime, queryTime, filterTime, dtoConvertTime);

        return result;
    }

    /**
     * 위치 기반 서비스 조회 (반경 검색)
     * 
     * @param latitude       위도
     * @param longitude      경도
     * @param radiusInMeters 반경 (미터 단위)
     * @param category       카테고리 (선택)
     * @param maxResults     최대 결과 수 (선택)
     * @return 검색 결과
     */
    public List<LocationServiceDTO> searchLocationServicesByLocation(
            Double latitude,
            Double longitude,
            Integer radiusInMeters,
            String category,
            Integer maxResults) {

        long methodStartTime = System.currentTimeMillis();
        log.info("📍 [위치 기반 검색] 시작 - latitude={}, longitude={}, radius={}m, category={}",
                latitude, longitude, radiusInMeters, category);

        // 반경 검색 수행
        long queryStartTime = System.currentTimeMillis();
        List<com.linkup.Petory.domain.location.entity.LocationService> services = locationServiceRepository
                .findByRadius(latitude, longitude, (double) radiusInMeters);
        long queryTime = System.currentTimeMillis() - queryStartTime;
        log.info("⏱️  [성능 측정] 위치 기반 DB 쿼리 실행 시간: {}ms, 조회된 레코드 수: {}개", queryTime, services.size());

        // 카테고리 필터링
        long filterStartTime = System.currentTimeMillis();
        long filterTime = 0;
        if (StringUtils.hasText(category) && !services.isEmpty()) {
            String categoryLower = category.toLowerCase(Locale.ROOT).trim();
            services = services.stream()
                    .filter(service -> {
                        // category3 우선 확인
                        if (service.getCategory3() != null) {
                            String cat3 = service.getCategory3().toLowerCase(Locale.ROOT).trim();
                            if (cat3.equals(categoryLower)) {
                                return true;
                            }
                        }
                        // category2 확인
                        if (service.getCategory2() != null) {
                            String cat2 = service.getCategory2().toLowerCase(Locale.ROOT).trim();
                            if (cat2.equals(categoryLower)) {
                                return true;
                            }
                        }
                        // category1 확인
                        if (service.getCategory1() != null) {
                            String cat1 = service.getCategory1().toLowerCase(Locale.ROOT).trim();
                            if (cat1.equals(categoryLower)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            filterTime = System.currentTimeMillis() - filterStartTime;
            log.info("⏱️  [성능 측정] 카테고리 필터링 시간: {}ms, 필터링 후 결과 수: {}개", filterTime, services.size());
        }

        // 최대 결과 수 제한 (null이거나 0이면 제한 없음)
        if (maxResults != null && maxResults > 0) {
            services = services.stream()
                    .limit(maxResults)
                    .collect(Collectors.toList());
            log.debug("결과 수 제한: maxResults={}, 제한 후={}개", maxResults, services.size());
        }

        // DTO로 변환
        long dtoConvertStartTime = System.currentTimeMillis();
        List<LocationServiceDTO> result = services.stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());
        long dtoConvertTime = System.currentTimeMillis() - dtoConvertStartTime;
        log.info("⏱️  [성능 측정] DTO 변환 시간: {}ms, 변환된 레코드 수: {}개", dtoConvertTime, result.size());

        long totalTime = System.currentTimeMillis() - methodStartTime;
        log.info("✅ [성능 측정] searchLocationServicesByLocation 전체 시간: {}ms (쿼리: {}ms, 필터링: {}ms, DTO변환: {}ms)",
                totalTime, queryTime, filterTime, dtoConvertTime);

        return result;
    }

    /**
     * 두 좌표 간 거리 계산 (Haversine 공식, 미터 단위)
     * 내 위치에서 각 서비스까지의 거리를 계산할 때 사용
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

        return R * c; // 미터 단위
    }

    /**
     * 위치 서비스 삭제 (Soft Delete)
     * 
     * @param serviceIdx 서비스 ID
     */
    @Transactional
    public void deleteService(Long serviceIdx) {
        LocationService service = locationServiceRepository.findById(serviceIdx)
                .orElseThrow(() -> new RuntimeException("서비스를 찾을 수 없습니다."));

        // 이미 삭제된 서비스인지 확인
        if (service.getIsDeleted() != null && service.getIsDeleted()) {
            throw new RuntimeException("이미 삭제된 서비스입니다.");
        }

        // Soft Delete 처리
        service.setIsDeleted(true);
        service.setDeletedAt(java.time.LocalDateTime.now());
        locationServiceRepository.save(service);

        log.info("위치 서비스가 삭제되었습니다. serviceIdx: {}", serviceIdx);
    }
}
