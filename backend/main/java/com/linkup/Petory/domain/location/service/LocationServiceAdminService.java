package com.linkup.Petory.domain.location.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.location.dto.KakaoPlaceDTO;
import com.linkup.Petory.domain.location.dto.LocationServiceLoadResponse;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceAdminService {

    private static final int ABSOLUTE_TOTAL_LIMIT = 50;
    private static final int MAX_KEYWORDS = 20;
    private static final List<String> DEFAULT_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "반려동물카페", "펫카페", "강아지카페", "고양이카페",
            "펫호텔", "애견호텔", "동물병원", "동물약국",
            "펫샵", "반려동물용품", "애견미용", "펫미용",
            "반려동물 놀이터"));

    private final KakaoMapService kakaoMapService;
    private final LocationServiceRepository locationServiceRepository;

    @Transactional
    public LocationServiceLoadResponse loadInitialData(String region,
            Integer maxResultsPerKeyword,
            String customKeywordsRaw) {

        String safeRegion = StringUtils.hasText(region) ? region.trim() : "서울특별시";
        int perKeywordLimit = resolvePerKeywordLimit(maxResultsPerKeyword);
        List<String> keywords = resolveKeywords(customKeywordsRaw);

        int fetchedCount = 0;
        int savedCount = 0;
        int duplicateCount = 0;
        int skippedCount = 0;

        Set<String> deduplicationKeys = new HashSet<>();
        List<LocationService> pendingSave = new ArrayList<>();

        for (String keyword : keywords) {
            if (savedCount >= ABSOLUTE_TOTAL_LIMIT) {
                break;
            }

            List<KakaoPlaceDTO.Document> documents = fetchPlaces(keyword, safeRegion, perKeywordLimit);
            fetchedCount += documents.size();

            for (KakaoPlaceDTO.Document document : documents) {
                if (savedCount >= ABSOLUTE_TOTAL_LIMIT) {
                    skippedCount++;
                    continue;
                }

                if (!StringUtils.hasText(document.getPlaceName()) || !StringUtils.hasText(document.getAddressName())) {
                    skippedCount++;
                    continue;
                }

                String dedupKey = buildDedupKey(document);
                if (dedupKey == null || deduplicationKeys.contains(dedupKey)) {
                    duplicateCount++;
                    continue;
                }

                // 주소: 도로명주소 우선, 없으면 지번주소
                String address = StringUtils.hasText(document.getRoadAddressName())
                        ? document.getRoadAddressName()
                        : document.getAddressName();

                if (locationServiceRepository.existsByNameAndAddress(document.getPlaceName(), address)) {
                    duplicateCount++;
                    continue;
                }

                LocationService entity = convertToEntity(document);
                pendingSave.add(entity);
                deduplicationKeys.add(dedupKey);
                savedCount++;
            }
        }

        if (!pendingSave.isEmpty()) {
            locationServiceRepository.saveAll(pendingSave);
        }

        String message = String.format("%s 지역에서 %d개의 장소를 저장했습니다. (중복 %d건, 스킵 %d건)",
                safeRegion, savedCount, duplicateCount, skippedCount);

        return LocationServiceLoadResponse.builder()
                .message(message)
                .region(safeRegion)
                .keywords(keywords)
                .keywordCount(keywords.size())
                .maxResultsPerKeyword(perKeywordLimit)
                .totalLimit(ABSOLUTE_TOTAL_LIMIT)
                .fetchedCount(fetchedCount)
                .savedCount(savedCount)
                .duplicateCount(duplicateCount)
                .skippedCount(skippedCount)
                .build();
    }

    private List<KakaoPlaceDTO.Document> fetchPlaces(String keyword, String region, int perKeywordLimit) {
        try {
            return kakaoMapService.searchPlaces(keyword, null, region,
                    null, null, null,
                    perKeywordLimit);
        } catch (Exception ex) {
            log.warn("키워드 [{}] 로 장소를 불러오는 중 오류 발생: {}", keyword, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> resolveKeywords(String customKeywordsRaw) {
        if (StringUtils.hasText(customKeywordsRaw)) {
            return Arrays.stream(customKeywordsRaw.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(MAX_KEYWORDS)
                    .collect(Collectors.toList());
        }
        return DEFAULT_KEYWORDS;
    }

    private int resolvePerKeywordLimit(Integer input) {
        if (input == null) {
            return 10;
        }
        return Math.min(Math.max(input, 1), ABSOLUTE_TOTAL_LIMIT);
    }

    private String buildDedupKey(KakaoPlaceDTO.Document document) {
        if (document == null) {
            return null;
        }
        String base = document.getPlaceName() + "|" + document.getAddressName();
        if (StringUtils.hasText(document.getRoadAddressName())) {
            base += "|" + document.getRoadAddressName();
        }
        return base;
    }

    private LocationService convertToEntity(KakaoPlaceDTO.Document document) {
        // 카카오맵 데이터는 기본 필드만 채우고, 공공데이터 상세 필드는 null
        // 카테고리 파싱
        String categoryName = document.getCategoryName();
        String[] categoryParts = categoryName != null ? categoryName.split(" > ") : new String[0];
        String category3 = categoryParts.length > 2 ? categoryParts[2] : categoryName;

        // 주소: 도로명주소 우선, 없으면 지번주소
        String address = StringUtils.hasText(document.getRoadAddressName())
                ? document.getRoadAddressName()
                : document.getAddressName();

        return LocationService.builder()
                .name(document.getPlaceName())
                // category 필드 제거됨
                .category1(categoryParts.length > 0 ? categoryParts[0] : null)
                .category2(categoryParts.length > 1 ? categoryParts[1] : null)
                .category3(category3) // 기본 카테고리
                .address(address) // 도로명주소 우선, 없으면 지번주소
                // detailAddress 필드 제거됨
                .latitude(parseDouble(document.getY()))
                .longitude(parseDouble(document.getX()))
                .phone(document.getPhone())
                .website(resolveWebsite(document))
                .description(document.getCategoryGroupName())
                .petFriendly(true)
                .dataSource("KAKAO") // 카카오맵 데이터임을 명시
                .build();
    }

    private Double parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveWebsite(KakaoPlaceDTO.Document document) {
        if (document == null) {
            return null;
        }
        if (StringUtils.hasText(document.getPlace_url())) {
            return document.getPlace_url();
        }
        if (StringUtils.hasText(document.getLink())) {
            return document.getLink();
        }
        return null;
    }
}
