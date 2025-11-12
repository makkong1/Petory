package com.linkup.Petory.service;

import com.linkup.Petory.dto.KakaoAddressDTO;
import com.linkup.Petory.dto.KakaoPlaceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 카카오 로컬 API를 사용하여 장소 정보를 가져오는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoMapService {

    private final RestTemplate restTemplate;

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    private static final String KAKAO_LOCAL_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String KAKAO_ADDRESS_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    /**
     * 키워드로 장소를 검색합니다.
     * 
     * @param keyword    검색 키워드 (예: "반려동물카페", "펫호텔")
     * @param category   카테고리 그룹 코드 (예: "FD6" - 음식점, "CE7" - 카페)
     * @param region     지역 (예: "서울특별시", "경기도")
     * @param maxResults 최대 검색 결과 수
     * @return 장소 목록
     */
    public List<KakaoPlaceDTO.Document> searchPlaces(String keyword,
            String category,
            String region,
            Double latitude,
            Double longitude,
            Integer radius,
            int maxResults) {
        List<KakaoPlaceDTO.Document> allPlaces = new ArrayList<>();
        int page = 1;
        int size = 15; // 카카오 API 최대 15개씩 반환

        String effectiveQuery = buildQuery(keyword, region);
        Integer safeRadius = resolveRadius(radius);

        try {
            while (allPlaces.size() < maxResults) {
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(KAKAO_LOCAL_SEARCH_URL)
                        .queryParam("query", effectiveQuery)
                        .queryParam("page", page)
                        .queryParam("size", Math.min(size, maxResults - allPlaces.size()));

                // null이거나 빈 값이 아닐 때만 파라미터 추가
                if (category != null && !category.isEmpty()) {
                    uriBuilder.queryParam("category_group_code", category);
                }

                if (longitude != null && latitude != null) {
                    uriBuilder.queryParam("x", longitude)
                            .queryParam("y", latitude);

                    if (safeRadius != null) {
                        uriBuilder.queryParam("radius", safeRadius);
                    }
                }

                URI uri = uriBuilder.encode(StandardCharsets.UTF_8).build().toUri();

                HttpHeaders headers = new HttpHeaders();
                String authHeader = "KakaoAK " + kakaoRestApiKey;
                headers.set("Authorization", authHeader);

                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<KakaoPlaceDTO> response = restTemplate.exchange(
                        uri,
                        HttpMethod.GET,
                        entity,
                        KakaoPlaceDTO.class);

                KakaoPlaceDTO responseBody = response.getBody();
                if (responseBody == null || responseBody.getDocuments() == null
                        || responseBody.getDocuments().isEmpty()) {
                    break;
                }

                allPlaces.addAll(responseBody.getDocuments());

                // 마지막 페이지인지 확인
                if (responseBody.getMeta() != null && Boolean.TRUE.equals(responseBody.getMeta().getIsEnd())) {
                    break;
                }

                page++;

                // API 호출 제한 방지를 위한 딜레이
                Thread.sleep(200);
            }

            return allPlaces.subList(0, Math.min(allPlaces.size(), maxResults));

        } catch (Exception e) {
            return allPlaces;
        }
    }

    private String buildQuery(String keyword, String region) {
        StringBuilder builder = new StringBuilder();

        if (keyword != null) {
            builder.append(keyword.trim());
        }

        if (region != null && !region.trim().isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(region.trim());
        }

        return builder.length() > 0 ? builder.toString() : "반려동물";
    }

    private Integer resolveRadius(Integer radius) {
        if (radius == null) {
            return 3000;
        }

        int safeRadius = Math.max(100, Math.min(radius, 20000));
        return safeRadius;
    }

    /**
     * 여러 키워드로 병렬 검색
     */
    public List<KakaoPlaceDTO.Document> searchPlacesByMultipleKeywords(List<String> keywords, String category,
            String region, int maxResultsPerKeyword) {
        List<KakaoPlaceDTO.Document> allPlaces = new ArrayList<>();

        for (String keyword : keywords) {
            try {
                List<KakaoPlaceDTO.Document> places = searchPlaces(keyword, category, region,
                        null, null, null,
                        maxResultsPerKeyword);
                allPlaces.addAll(places);
            } catch (Exception e) {
                // 에러 무시
            }
        }

        return allPlaces;
    }

    /**
     * 주소를 위도/경도로 변환 (Geocoding)
     * 
     * @param address 변환할 주소
     * @return 위도, 경도 정보가 담긴 배열 [latitude, longitude], 변환 실패 시 null
     */
    public Double[] addressToCoordinates(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }

        try {
            URI uri = UriComponentsBuilder.fromUriString(KAKAO_ADDRESS_SEARCH_URL)
                    .queryParam("query", address)
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoRestApiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<KakaoAddressDTO> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    entity,
                    KakaoAddressDTO.class);

            KakaoAddressDTO responseBody = response.getBody();
            if (responseBody == null || responseBody.getDocuments() == null || responseBody.getDocuments().isEmpty()) {
                return null;
            }

            KakaoAddressDTO.Document doc = responseBody.getDocuments().get(0);

            // 좌표는 최상위, road_address, address 순으로 확인
            String latitudeStr = doc.getY();
            String longitudeStr = doc.getX();

            if (latitudeStr == null || longitudeStr == null) {
                if (doc.getRoadAddress() != null) {
                    latitudeStr = doc.getRoadAddress().getY();
                    longitudeStr = doc.getRoadAddress().getX();
                } else if (doc.getAddress() != null) {
                    latitudeStr = doc.getAddress().getY();
                    longitudeStr = doc.getAddress().getX();
                }
            }

            if (latitudeStr != null && longitudeStr != null) {
                try {
                    Double latitude = Double.parseDouble(latitudeStr);
                    Double longitude = Double.parseDouble(longitudeStr);
                    return new Double[] { latitude, longitude };
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
