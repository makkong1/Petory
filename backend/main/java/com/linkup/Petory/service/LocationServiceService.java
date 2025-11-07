package com.linkup.Petory.service;

import com.linkup.Petory.dto.KakaoPlaceDTO;
import com.linkup.Petory.dto.LocationServiceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceService {

    private final KakaoMapService kakaoMapService;

    public List<LocationServiceDTO> searchKakaoPlaces(String keyword,
            String region,
            Double latitude,
            Double longitude,
            Integer radius,
            Integer maxResults) {

        String effectiveKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "반려동물";
        log.info("카카오 장소 검색 요청 수신 - keyword='{}', region='{}', latitude={}, longitude={}, radius={}, size={}",
                effectiveKeyword,
                region,
                latitude,
                longitude,
                radius,
                maxResults);

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

    private int resolveMaxResults(Integer maxResults) {
        if (maxResults == null || maxResults <= 0) {
            return 10;
        }
        return Math.min(maxResults, 10);
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
                .petFriendly(null)
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
