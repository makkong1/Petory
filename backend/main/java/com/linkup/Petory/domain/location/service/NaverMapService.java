package com.linkup.Petory.domain.location.service;

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

import java.util.HashMap;
import java.util.Map;

/**
 * 네이버맵 API 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverMapService {

    @Value("${naver.map.api.client-id:}")
    private String apiKeyId;

    @Value("${naver.map.api.client-secret:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 네이버맵 Directions API 호출
     * @param startLng 출발지 경도
     * @param startLat 출발지 위도
     * @param endLng 도착지 경도
     * @param endLat 도착지 위도
     * @param option 경로 옵션 (traoptimal=최적, trafast=최단, tracomfort=편한길)
     * @return 길찾기 결과
     */
    public Map<String, Object> getDirections(double startLng, double startLat, double endLng, double endLat, String option) {
        try {
            // API 키가 없으면 에러 반환
            if (apiKeyId == null || apiKeyId.isEmpty() || apiKey == null || apiKey.isEmpty()) {
                log.warn("네이버맵 API 키가 설정되지 않았습니다. apiKeyId: {}, apiKey: {}", apiKeyId, apiKey != null ? "***" : null);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "네이버맵 API 키가 설정되지 않았습니다.");
                return errorResponse;
            }

            log.info("네이버맵 Directions API 호출 - 출발지: ({}, {}), 도착지: ({}, {})", startLng, startLat, endLng, endLat);
            log.debug("API Key ID: {}, API Key: {}", apiKeyId, apiKey.substring(0, Math.min(5, apiKey.length())) + "***");

            // 네이버맵 Directions API URL (공식 예시에 따름)
            String url = UriComponentsBuilder.fromUriString("https://maps.apigw.ntruss.com/map-direction/v1/driving")
                    .queryParam("start", startLng + "," + startLat) // 경도,위도 순서
                    .queryParam("goal", endLng + "," + endLat)
                    .queryParam("option", option != null ? option : "traoptimal")
                    .toUriString();

            log.debug("요청 URL: {}", url);

            // 헤더 설정 (공식 예시에 따름 - 소문자)
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-ncp-apigw-api-key-id", apiKeyId);
            headers.set("x-ncp-apigw-api-key", apiKey);
            
            log.debug("요청 헤더 - x-ncp-apigw-api-key-id: {}, x-ncp-apigw-api-key: {}", apiKeyId, apiKey.substring(0, Math.min(5, apiKey.length())) + "***");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // API 호출
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            log.info("네이버맵 API 응답 상태: {}", response.getStatusCode());

            Map<String, Object> result = new HashMap<>();
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                result.put("success", true);
                result.put("data", response.getBody());
                log.info("네이버맵 Directions API 호출 성공");
                // 응답 데이터 구조 로깅
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null) {
                    if (responseBody.containsKey("route")) {
                        log.info("경로 정보 수신 완료 - route 데이터 존재");
                        Object route = responseBody.get("route");
                        if (route instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> routeMap = (Map<String, Object>) route;
                            if (routeMap.containsKey("traoptimal")) {
                                log.info("최적 경로(traoptimal) 데이터 존재");
                            }
                        }
                    } else {
                        log.warn("응답에 route 데이터가 없습니다. 응답 구조: {}", responseBody.keySet());
                    }
                }
            } else {
                result.put("success", false);
                result.put("message", "길찾기 API 호출 실패");
                result.put("statusCode", response.getStatusCode().value());
                result.put("responseBody", response.getBody());
                log.warn("네이버맵 Directions API 호출 실패 - 상태: {}, 응답: {}", response.getStatusCode(), response.getBody());
            }

            return result;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("네이버맵 Directions API HTTP 에러: {} - 상태: {}, 응답: {}", e.getMessage(), e.getStatusCode(), responseBody);
            
            // 401 에러이고 "subscription required" 메시지인 경우
            if (e.getStatusCode() != null && e.getStatusCode().value() == 401 && 
                responseBody != null && responseBody.contains("subscription")) {
                log.warn("네이버맵 Directions API 구독이 필요합니다. 웹 URL 방식은 정상 작동합니다.");
            }
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("statusCode", e.getStatusCode() != null ? e.getStatusCode().value() : null);
            errorResponse.put("message", "네이버맵 Directions API 구독이 필요합니다. 웹 URL 방식은 정상 작동합니다.");
            errorResponse.put("responseBody", responseBody);
            return errorResponse;
        } catch (Exception e) {
            log.error("네이버맵 Directions API 호출 실패: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
}

