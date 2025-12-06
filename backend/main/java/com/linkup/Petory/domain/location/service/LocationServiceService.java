package com.linkup.Petory.domain.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.location.converter.LocationServiceConverter;
import com.linkup.Petory.domain.location.dto.KakaoPlaceDTO;
import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceService {

    private final KakaoMapService kakaoMapService;
    private final LocationServiceConverter locationServiceConverter;
    private final LocationServiceRepository locationServiceRepository;

    private static final List<String> PET_KEYWORDS = Arrays.asList(
            "반려", "애완", "애견", "펫", "pet", "도그", "캣", "dog", "cat",
            "동물병원", "동물약국", "동물미용", "동물호텔", "동물용품", "동물카페", "동물운동장", "반려동물",
            "펫카페", "펫샵", "펫숍", "고양이", "강아지", "펫살롱", "펫미용", "펫호텔", "펫유치원", "펫놀이터", "펫스쿨");

    private static final CategoryConfig HOSPITAL_CATEGORY = CategoryConfig.of(
            "반려동물 병원",
            List.of("동물병원", "수의", "수의사", "반려동물병원"),
            Set.of("HP8"));

    private static final CategoryConfig CAFE_CATEGORY = CategoryConfig.of(
            "애견카페",
            List.of("애견카페", "반려동물카페", "펫카페", "애완동물카페"),
            Set.of("CE7"));

    private static final CategoryConfig PLAYGROUND_CATEGORY = CategoryConfig.of(
            "반려동물 놀이터",
            List.of("애견놀이터", "반려견놀이터", "반려동물 놀이터", "애견운동장"),
            Set.of("AT4", "CT1", "PS3"));

    private static final Map<String, CategoryConfig> CATEGORY_CONFIGS = Map.of(
            "HOSPITAL", HOSPITAL_CATEGORY,
            "CAFE", CAFE_CATEGORY,
            "PLAYGROUND", PLAYGROUND_CATEGORY);

    public List<LocationServiceDTO> searchKakaoPlaces(String keyword,
            String region,
            Double latitude,
            Double longitude,
            Integer radius,
            Integer maxResults,
            String categoryType) {

        String effectiveKeyword = buildEffectiveKeyword(keyword, categoryType);
        int safeMaxResults = resolveMaxResults(maxResults);

        List<KakaoPlaceDTO.Document> documents = kakaoMapService.searchPlaces(
                effectiveKeyword,
                null,
                region,
                latitude,
                longitude,
                radius,
                safeMaxResults);

        List<LocationServiceDTO> results = documents.stream()
                .filter(this::isLikelyPetFriendly)
                .filter(document -> matchesCategory(document, categoryType))
                .map(locationServiceConverter::fromKakaoDocument)
                .collect(Collectors.toList());

        return results;
    }

    private String buildEffectiveKeyword(String keyword, String categoryType) {
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        CategoryConfig categoryConfig = resolveCategoryConfig(categoryType);
        String categoryKeyword = categoryConfig != null ? categoryConfig.getPreferredKeyword() : null;

        StringBuilder builder = new StringBuilder();

        if (StringUtils.hasText(trimmedKeyword)) {
            builder.append(trimmedKeyword);
        }

        if (StringUtils.hasText(categoryKeyword)) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(categoryKeyword);
        }

        if (builder.length() == 0 || !containsAnyPetKeyword(builder.toString())) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append("반려동물");
        }

        return builder.toString();
    }

    private boolean containsAnyPetKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return PET_KEYWORDS.stream()
                .anyMatch(keyword -> lower.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private boolean matchesCategory(KakaoPlaceDTO.Document document, String categoryType) {
        CategoryConfig categoryConfig = resolveCategoryConfig(categoryType);
        if (categoryConfig == null) {
            return true;
        }

        String combined = (defaultString(document.getPlaceName()) + " " + defaultString(document.getCategoryName()))
                .toLowerCase(Locale.ROOT);

        boolean keywordMatch = categoryConfig.getNameKeywords().stream()
                .anyMatch(keyword -> combined.contains(keyword.toLowerCase(Locale.ROOT)));

        String categoryGroupCode = defaultString(document.getCategoryGroupCode());
        boolean categoryGroupMatch = StringUtils.hasText(categoryGroupCode) && categoryConfig.getCategoryGroupCodes()
                .stream()
                .anyMatch(code -> code.equalsIgnoreCase(categoryGroupCode));

        return keywordMatch || categoryGroupMatch;
    }

    private CategoryConfig resolveCategoryConfig(String categoryType) {
        if (!StringUtils.hasText(categoryType)) {
            return null;
        }
        return CATEGORY_CONFIGS.get(categoryType.trim().toUpperCase(Locale.ROOT));
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private static final class CategoryConfig {
        private final String preferredKeyword;
        private final List<String> nameKeywords;
        private final Set<String> categoryGroupCodes;

        private CategoryConfig(String preferredKeyword, List<String> nameKeywords, Set<String> categoryGroupCodes) {
            this.preferredKeyword = preferredKeyword;
            this.nameKeywords = nameKeywords != null ? List.copyOf(nameKeywords) : List.of();
            this.categoryGroupCodes = categoryGroupCodes != null ? Set.copyOf(categoryGroupCodes) : Set.of();
        }

        static CategoryConfig of(String preferredKeyword, List<String> nameKeywords, Set<String> categoryGroupCodes) {
            return new CategoryConfig(preferredKeyword, nameKeywords, categoryGroupCodes);
        }

        String getPreferredKeyword() {
            return preferredKeyword;
        }

        List<String> getNameKeywords() {
            return nameKeywords;
        }

        Set<String> getCategoryGroupCodes() {
            return categoryGroupCodes;
        }
    }

    private boolean isLikelyPetFriendly(KakaoPlaceDTO.Document document) {
        if (document == null) {
            return false;
        }

        StringBuilder combined = new StringBuilder();
        appendIfHasText(combined, document.getPlaceName());
        appendIfHasText(combined, document.getCategoryName());
        appendIfHasText(combined, document.getRoadAddressName());
        appendIfHasText(combined, document.getAddressName());
        appendIfHasText(combined, document.getCategoryGroupName());

        if (combined.length() == 0) {
            return false;
        }

        return containsAnyPetKeyword(combined.toString());
    }

    private void appendIfHasText(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(value.trim());
        }
    }

    private int resolveMaxResults(Integer maxResults) {
        if (maxResults == null || maxResults <= 0) {
            return 200; // 기본값을 200으로 증가
        }
        return Math.min(maxResults, 200); // 최대값을 200으로 증가
    }

    // 인기 위치 서비스 캐싱
    @Cacheable(value = "popularLocationServices", key = "#category")
    public List<LocationServiceDTO> getPopularLocationServices(String category) {
        return locationServiceRepository.findTop10ByCategoryOrderByRatingDesc(category)
                .stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());
    }

    // DB에서 위치 서비스 검색 (반경, 지역, 키워드 기반)
    public List<LocationServiceDTO> searchLocationServices(
            String keyword,
            String region,
            Double latitude,
            Double longitude,
            Integer radius,
            Integer maxResults,
            String category) {

        List<com.linkup.Petory.domain.location.entity.LocationService> services;

        // 1. 키워드 검색 우선
        if (StringUtils.hasText(keyword)) {
            services = locationServiceRepository.findByNameContaining(keyword);
        }
        // 2. 지역 검색
        else if (StringUtils.hasText(region)) {
            services = locationServiceRepository.findByAddressContaining(region);
        }
        // 3. 위도/경도가 있으면 반경 검색
        else if (latitude != null && longitude != null) {
            // 반경 기본값: 3000m (3km), 초기 로드 시 넓은 범위(20km) 사용
            double radiusInMeters = (radius != null && radius > 0) ? radius : 3000.0;
            // 초기 로드(radius가 null이거나 기본값)이면 넓은 범위로 검색
            if (radius == null || radius <= 3000) {
                radiusInMeters = 20000.0; // 20km로 넓은 범위 검색
            }
            services = locationServiceRepository.findByRadius(latitude, longitude, radiusInMeters);
        }
        // 4. 모두 없으면 전체 조회 (평점순)
        else {
            services = locationServiceRepository.findByOrderByRatingDesc();
        }

        // services가 null이면 빈 리스트로 초기화
        if (services == null) {
            services = new java.util.ArrayList<>();
        }

        // 위도/경도가 있고 반경 검색 결과가 있으면, 추가로 지역 필터링 적용
        if (latitude != null && longitude != null && StringUtils.hasText(region) && !services.isEmpty()) {
            String regionLower = region.toLowerCase(Locale.ROOT);
            services = services.stream()
                    .filter(service -> {
                        String address = service.getAddress() != null ? service.getAddress().toLowerCase(Locale.ROOT)
                                : "";
                        return address.contains(regionLower);
                    })
                    .collect(Collectors.toList());
        }

        // 카테고리 필터링 (category3, category2, category1 순서로 확인)
        if (StringUtils.hasText(category)) {
            services = services.stream()
                    .filter(service -> {
                        String serviceCategory = service.getCategory3() != null ? service.getCategory3()
                                : service.getCategory2() != null ? service.getCategory2() : service.getCategory1();
                        return serviceCategory != null &&
                                (serviceCategory.toLowerCase(Locale.ROOT).contains(category.toLowerCase(Locale.ROOT))
                                        || category.toLowerCase(Locale.ROOT)
                                                .contains(serviceCategory.toLowerCase(Locale.ROOT)));
                    })
                    .collect(Collectors.toList());
        }

        // 최대 결과 수 제한
        if (maxResults != null && maxResults > 0) {
            services = services.stream()
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        // DTO로 변환
        return services.stream()
                .map(locationServiceConverter::toDTO)
                .collect(Collectors.toList());
    }
}
