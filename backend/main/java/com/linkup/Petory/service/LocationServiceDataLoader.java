package com.linkup.Petory.service;

import com.linkup.Petory.dto.KakaoPlaceDTO;
import com.linkup.Petory.entity.LocationService;
import com.linkup.Petory.repository.LocationServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 애플리케이션 시작 시 LocationService 초기 데이터를 로드하는 클래스
 * 
 * 주의: application.properties에서 data.loader.enabled=true로 설정해야 실행됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class LocationServiceDataLoader implements CommandLineRunner {

    private final LocationServiceRepository locationServiceRepository;
    private final KakaoMapService kakaoMapService;
    private final Random random = new Random();

    // application.properties에서 활성화 여부 확인
    private boolean isEnabled() {
        // 필요시 @Value로 설정값 받아오기
        // 기본적으로는 수동 실행을 위해 false로 설정 권장
        return false; // true로 변경하면 애플리케이션 시작 시 자동 실행
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!isEnabled()) {
            log.info("LocationService 초기 데이터 로더가 비활성화되어 있습니다.");
            return;
        }

        if (locationServiceRepository.count() > 0) {
            log.info("LocationService 테이블에 이미 데이터가 있습니다. 초기 데이터를 로드하지 않습니다.");
            return;
        }

        log.info("LocationService 초기 데이터 로딩을 시작합니다...");

        // 검색할 키워드 목록
        List<String> keywords = Arrays.asList(
                "반려동물카페",
                "펫카페",
                "강아지카페",
                "고양이카페",
                "펫호텔",
                "애견호텔",
                "동물병원",
                "반려동물병원",
                "펫샵",
                "반려동물용품",
                "애견미용",
                "펫미용",
                "강아지미용",
                "반려동물장례",
                "펫장례"
        );

        // 지역 목록
        List<String> regions = Arrays.asList(
                "서울특별시",
                "경기도",
                "인천광역시",
                "부산광역시",
                "대구광역시",
                "대전광역시",
                "광주광역시",
                "울산광역시"
        );

        List<LocationService> servicesToSave = new ArrayList<>();

        // 각 지역별로 검색
        for (String region : regions) {
            log.info("{} 지역의 장소를 검색합니다...", region);
            
            // 각 키워드로 검색 (지역당 최대 10개씩)
            for (String keyword : keywords) {
                try {
                    List<KakaoPlaceDTO.Document> places = kakaoMapService.searchPlaces(
                            keyword, 
                            null, // 카테고리 코드 (null이면 전체)
                            region, 
                            10
                    );

                    for (KakaoPlaceDTO.Document place : places) {
                        // 중복 체크 (이름과 주소로)
                        if (locationServiceRepository.findByNameAndAddress(
                                place.getPlaceName(), 
                                place.getAddressName() != null ? place.getAddressName() : place.getRoadAddressName()
                        ).isEmpty()) {
                            
                            LocationService service = convertKakaoPlaceToLocationService(place, keyword);
                            servicesToSave.add(service);
                        }
                    }

                    // API 호출 제한 방지
                    Thread.sleep(300);
                } catch (Exception e) {
                    log.error("키워드 '{}' 지역 '{}' 검색 중 오류: {}", keyword, region, e.getMessage());
                }
            }
        }

        if (!servicesToSave.isEmpty()) {
            locationServiceRepository.saveAll(servicesToSave);
            log.info("총 {}개의 LocationService 데이터를 저장했습니다.", servicesToSave.size());
        } else {
            log.warn("저장할 LocationService 데이터가 없습니다.");
        }
    }

    /**
     * 카카오 API 응답을 LocationService 엔티티로 변환
     */
    private LocationService convertKakaoPlaceToLocationService(KakaoPlaceDTO.Document place, String keyword) {
        // 카테고리 매핑
        String category = mapCategory(place.getCategoryGroupCode(), keyword);
        
        // 좌표 변환 (String -> Double)
        Double longitude = null;
        Double latitude = null;
        try {
            if (place.getX() != null && !place.getX().isEmpty()) {
                longitude = Double.parseDouble(place.getX());
            }
            if (place.getY() != null && !place.getY().isEmpty()) {
                latitude = Double.parseDouble(place.getY());
            }
        } catch (NumberFormatException e) {
            log.warn("좌표 변환 실패: {}", e.getMessage());
        }

        // 랜덤한 운영 시간 생성 (예시)
        LocalTime openingTime = LocalTime.of(9 + random.nextInt(2), 0); // 9시~10시 사이
        LocalTime closingTime = LocalTime.of(20 + random.nextInt(3), 0); // 20시~22시 사이

        // 랜덤 평점 생성 (3.5 ~ 5.0)
        Double rating = 3.5 + random.nextDouble() * 1.5;
        rating = Math.round(rating * 10.0) / 10.0; // 소수점 1자리

        return LocationService.builder()
                .name(place.getPlaceName())
                .category(category)
                .address(place.getAddressName() != null ? place.getAddressName() : "")
                .detailAddress(place.getRoadAddressName() != null ? place.getRoadAddressName() : "")
                .latitude(latitude)
                .longitude(longitude)
                .phone(place.getPhone() != null ? place.getPhone().replace("-", "") : null)
                .openingTime(openingTime)
                .closingTime(closingTime)
                .rating(rating)
                .description(place.getCategoryName() != null ? place.getCategoryName() : category)
                .imageUrl(place.getPlace_url())
                .website(place.getLink())
                .petFriendly(true) // 카카오맵에서 반려동물 관련 검색 결과이므로 기본값 true
                .petPolicy(generatePetPolicy(keyword, category))
                .build();
    }

    /**
     * 카카오 API 카테고리 코드를 우리 시스템 카테고리로 매핑
     */
    private String mapCategory(String categoryGroupCode, String keyword) {
        if (categoryGroupCode == null) {
            if (keyword.contains("카페")) return "카페";
            if (keyword.contains("호텔")) return "호텔";
            if (keyword.contains("병원")) return "병원";
            if (keyword.contains("샵") || keyword.contains("용품")) return "샵";
            if (keyword.contains("미용")) return "미용";
            return "기타";
        }

        switch (categoryGroupCode) {
            case "CE7": return "카페";
            case "FD6": return "식당";
            case "HP8": return "병원";
            case "MT1": return "마트";
            case "CS2": return "편의점";
            case "PK1": return "주차장";
            default: return "기타";
        }
    }

    /**
     * 키워드와 카테고리에 따라 펫 정책 생성
     */
    private String generatePetPolicy(String keyword, String category) {
        if (keyword.contains("호텔")) {
            return "반려동물 동반 입장 가능. 예약 시 반려동물 정보를 알려주세요.";
        } else if (keyword.contains("카페")) {
            return "반려동물 동반 입장 가능. 실내/야외 이용 가능.";
        } else if (keyword.contains("병원")) {
            return "반려동물 진료 전문 병원입니다.";
        } else if (keyword.contains("미용")) {
            return "반려동물 전문 미용 서비스를 제공합니다. 예약 필수.";
        } else if (keyword.contains("샵") || keyword.contains("용품")) {
            return "반려동물 용품 전문 매장입니다.";
        }
        return "반려동물 관련 서비스를 제공합니다.";
    }

    /**
     * 수동으로 초기 데이터를 로드하는 메서드 (관리자용)
     * 전체 최대 50개로 제한됩니다.
     */
    @Transactional
    public void loadInitialDataManually(String region, int maxResultsPerKeyword) {
        final int MAX_TOTAL_RESULTS = 50; // 전체 최대 50개로 제한
        log.info("수동으로 LocationService 초기 데이터를 로드합니다. 지역: {}, 전체 최대: {}개", region, MAX_TOTAL_RESULTS);

        List<String> keywords = Arrays.asList(
                "반려동물카페",
                "펫카페",
                "강아지카페",
                "고양이카페",
                "펫호텔",
                "동물병원",
                "펫샵",
                "반려동물용품",
                "애견미용"
        );

        List<LocationService> servicesToSave = new ArrayList<>();

        for (String keyword : keywords) {
            // 이미 50개를 다 모았으면 중단
            if (servicesToSave.size() >= MAX_TOTAL_RESULTS) {
                log.info("전체 최대 개수({}개)에 도달했습니다. 검색을 중단합니다.", MAX_TOTAL_RESULTS);
                break;
            }

            try {
                // 남은 개수만큼만 검색 (전체 제한을 고려)
                int remainingSlots = MAX_TOTAL_RESULTS - servicesToSave.size();
                int searchCount = Math.min(maxResultsPerKeyword, remainingSlots);

                List<KakaoPlaceDTO.Document> places = kakaoMapService.searchPlaces(
                        keyword, 
                        null, 
                        region, 
                        searchCount
                );

                for (KakaoPlaceDTO.Document place : places) {
                    // 50개 제한 체크
                    if (servicesToSave.size() >= MAX_TOTAL_RESULTS) {
                        break;
                    }

                    if (locationServiceRepository.findByNameAndAddress(
                            place.getPlaceName(), 
                            place.getAddressName() != null ? place.getAddressName() : place.getRoadAddressName()
                    ).isEmpty()) {
                        
                        LocationService service = convertKakaoPlaceToLocationService(place, keyword);
                        servicesToSave.add(service);
                    }
                }

                Thread.sleep(300);
            } catch (Exception e) {
                log.error("키워드 '{}' 검색 중 오류: {}", keyword, e.getMessage());
            }
        }

        if (!servicesToSave.isEmpty()) {
            locationServiceRepository.saveAll(servicesToSave);
            log.info("총 {}개의 LocationService 데이터를 저장했습니다. (최대 제한: {}개)", servicesToSave.size(), MAX_TOTAL_RESULTS);
        } else {
            log.warn("저장할 LocationService 데이터가 없습니다.");
        }
    }
}

