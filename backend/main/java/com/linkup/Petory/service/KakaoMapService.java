package com.linkup.Petory.service;

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

    /**
     * 키워드로 장소를 검색합니다.
     * 
     * @param keyword    검색 키워드 (예: "반려동물카페", "펫호텔")
     * @param category   카테고리 그룹 코드 (예: "FD6" - 음식점, "CE7" - 카페)
     * @param region     지역 (예: "서울특별시", "경기도")
     * @param maxResults 최대 검색 결과 수
     * @return 장소 목록
     */
    public List<KakaoPlaceDTO.Document> searchPlaces(String keyword, String category, String region, int maxResults) {
        List<KakaoPlaceDTO.Document> allPlaces = new ArrayList<>();
        int page = 1;
        int size = 15; // 카카오 API 최대 15개씩 반환

        try {
            while (allPlaces.size() < maxResults) {
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(KAKAO_LOCAL_SEARCH_URL)
                        .queryParam("query", keyword)
                        .queryParam("page", page)
                        .queryParam("size", Math.min(size, maxResults - allPlaces.size()));

                // null이거나 빈 값이 아닐 때만 파라미터 추가
                if (category != null && !category.isEmpty()) {
                    uriBuilder.queryParam("category_group_code", category);
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

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("카카오맵 API 호출 중 HTTP 오류 발생: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return allPlaces;
        } catch (Exception e) {
            log.error("카카오맵 API 호출 중 오류 발생: {}", e.getMessage());
            return allPlaces;
        }
    }

    /**
     * 여러 키워드로 병렬 검색
     */
    public List<KakaoPlaceDTO.Document> searchPlacesByMultipleKeywords(List<String> keywords, String category,
            String region, int maxResultsPerKeyword) {
        List<KakaoPlaceDTO.Document> allPlaces = new ArrayList<>();

        for (String keyword : keywords) {
            try {
                List<KakaoPlaceDTO.Document> places = searchPlaces(keyword, category, region, maxResultsPerKeyword);
                allPlaces.addAll(places);
                log.info("키워드 '{}'로 {}개의 장소를 검색했습니다.", keyword, places.size());
            } catch (Exception e) {
                log.error("키워드 '{}' 검색 중 오류 발생: {}", keyword, e.getMessage());
            }
        }

        return allPlaces;
    }
}
