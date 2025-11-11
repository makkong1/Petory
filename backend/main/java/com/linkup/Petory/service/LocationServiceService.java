package com.linkup.Petory.service;

import com.linkup.Petory.dto.KakaoPlaceDTO;
import com.linkup.Petory.dto.LocationServiceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        log.info("카카오 장소 검색 요청 수신 - keyword='{}', region='{}', latitude={}, longitude={}, radius={}, size={}, categoryType={}",
                effectiveKeyword,
                region,
                latitude,
                longitude,
                radius,
                maxResults,
                categoryType);

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
                .map(this::convertDocumentToDto)
                .collect(Collectors.toList());

        log.info("카카오 장소 검색 완료 - 응답 {}건", results.size());

        if (log.isDebugEnabled()) {
            results.forEach(dto -> log.debug("카카오 장소 결과 - id={}, name='{}', address='{}', placeUrl='{}'",
                    dto.getExternalId(),
                    dto.getName(),
                    dto.getAddress(),
                    dto.getPlaceUrl()));
        }

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

        boolean petFriendly = containsAnyPetKeyword(combined.toString());

        if (!petFriendly && log.isDebugEnabled()) {
            log.debug("필터링된 장소 (반려동물 관련 키워드 없음): id={}, name='{}', category='{}'",
                    document.getId(),
                    document.getPlaceName(),
                    document.getCategoryName());
        }

        return petFriendly;
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
            return 100;
        }
        return Math.min(maxResults, 100);
    }

    private LocationServiceDTO convertDocumentToDto(KakaoPlaceDTO.Document document) {
        Double latitude = parseDoubleOrNull(document.getY());
        Double longitude = parseDoubleOrNull(document.getX());

        return LocationServiceDTO.builder()
                .idx(null)
                .externalId(document.getId())
                .name(document.getPlaceName())
                .category(document.getCategoryName())
                .address(document.getAddressName())
                .detailAddress(document.getRoadAddressName())
                .latitude(latitude)
                .longitude(longitude)
                .rating(null)
                .phone(document.getPhone())
                .openingTime(null)
                .closingTime(null)
                .imageUrl(null)
                .website(document.getPlace_url())
                .placeUrl(document.getPlace_url())
                .description(document.getCategoryName())
                .petFriendly(true)
                .petPolicy(null)
                .reviewCount(null)
                .reviews(null)
                .build();
    }

    private Double parseDoubleOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            log.warn("좌표 값을 Double로 변환하지 못했습니다. value={}", value);
            return null;
        }
    }
}
