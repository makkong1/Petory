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
     * 
     * @param startLng 출발지 경도
     * @param startLat 출발지 위도
     * @param endLng   도착지 경도
     * @param endLat   도착지 위도
     * @param option   경로 옵션 (traoptimal=최적, trafast=최단, tracomfort=편한길)
     * @return 길찾기 결과
     */
    public Map<String, Object> getDirections(double startLng, double startLat, double endLng, double endLat,
            String option) {
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
            log.debug("API Key ID: {}, API Key: {}", apiKeyId,
                    apiKey.substring(0, Math.min(5, apiKey.length())) + "***");

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

            log.debug("요청 헤더 - x-ncp-apigw-api-key-id: {}, x-ncp-apigw-api-key: {}", apiKeyId,
                    apiKey.substring(0, Math.min(5, apiKey.length())) + "***");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // API 호출
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });

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
            log.warn("네이버맵 Directions API HTTP 에러: {} - 상태: {}, 응답: {}", e.getMessage(), e.getStatusCode(),
                    responseBody);

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

    /**
     * 네이버맵 지오코딩 (주소를 좌표로 변환)
     * 
     * @param address 변환할 주소
     * @return 위도, 경도 정보가 담긴 배열 [latitude, longitude], 변환 실패 시 null
     */
    public Double[] addressToCoordinates(String address) {
        log.info("📍 [NaverMapService] addressToCoordinates 호출됨 - 주소: {}", address);

        if (address == null || address.trim().isEmpty()) {
            log.warn("⚠️ [NaverMapService] 주소가 null이거나 비어있음");
            return null;
        }

        try {
            log.info("🔑 [NaverMapService] API 키 확인 중...");
            log.info("🔑 [NaverMapService] apiKeyId: {}",
                    apiKeyId != null && !apiKeyId.isEmpty() ? apiKeyId : "null 또는 비어있음");
            log.info("🔑 [NaverMapService] apiKey: {}",
                    apiKey != null && !apiKey.isEmpty() ? (apiKey.substring(0, Math.min(5, apiKey.length())) + "***")
                            : "null 또는 비어있음");

            // API 키가 없으면 에러 반환
            if (apiKeyId == null || apiKeyId.isEmpty() || apiKey == null || apiKey.isEmpty()) {
                log.error(
                        "❌ [NaverMapService] 네이버맵 API 키가 설정되지 않았습니다. application.properties에서 naver.map.api.client-id와 naver.map.api.client-secret을 확인하세요.");
                log.error("❌ [NaverMapService] 현재 apiKeyId: {}, apiKey: {}",
                        apiKeyId != null ? apiKeyId : "null",
                        apiKey != null
                                ? (apiKey.length() > 0 ? apiKey.substring(0, Math.min(5, apiKey.length())) + "***"
                                        : "비어있음")
                                : "null");
                return null;
            }

            log.info("✅ [NaverMapService] API 키 확인 완료 - 네이버맵 지오코딩 API 호출 시작 - 주소: {}", address);
            log.info("📍 [NaverMapService] 주소 상세 - 길이: {}, 공백 포함: {}, + 포함: {}",
                    address.length(), address.contains(" "), address.contains("+"));

            // 주소 정리: + 문자를 공백으로 변환하고 공백을 하나로 통일
            String cleanedAddress = address.replace("+", " ").replaceAll("\\s+", " ").trim();
            log.info("🧹 [NaverMapService] 정리된 주소: {}", cleanedAddress);

            // 네이버맵 Geocoding API URL (지오코딩) - 공식 문서에 따름
            // 공식 엔드포인트: https://maps.apigw.ntruss.com/map-geocode/v2/geocode
            String url = UriComponentsBuilder
                    .fromUriString("https://maps.apigw.ntruss.com/map-geocode/v2/geocode")
                    .queryParam("query", cleanedAddress)
                    .encode() // URL 인코딩 자동 처리
                    .toUriString();

            log.info("🌐 [NaverMapService] 요청 URL: {}", url);

            // 헤더 설정 (공식 문서에 따름 - 소문자)
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-ncp-apigw-api-key-id", apiKeyId);
            headers.set("x-ncp-apigw-api-key", apiKey);
            headers.set("Accept", "application/json");

            log.info("🔑 [NaverMapService] 요청 헤더 설정 완료 - apiKeyId: {}, apiKey: {}", apiKeyId,
                    apiKey.substring(0, Math.min(5, apiKey.length())) + "***");
            log.info("🔑 [NaverMapService] 헤더 상세 - x-ncp-apigw-api-key-id 존재: {}, x-ncp-apigw-api-key 존재: {}",
                    headers.containsKey("x-ncp-apigw-api-key-id"), headers.containsKey("x-ncp-apigw-api-key"));
            log.info("🔑 [NaverMapService] 모든 헤더: {}", headers);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // API 호출
            log.info("📡 [NaverMapService] API 호출 시작...");
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });

            log.info("📥 [NaverMapService] 네이버맵 지오코딩 API 응답 상태: {}", response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("📦 [NaverMapService] 응답 본문 전체: {}", responseBody);
                if (responseBody != null) {
                    log.info("📦 [NaverMapService] 응답 키 목록: {}", responseBody.keySet());
                }

                // 네이버맵 지오코딩 응답 파싱
                if (responseBody != null && responseBody.containsKey("addresses")) {
                    @SuppressWarnings("unchecked")
                    java.util.List<?> addressesList = (java.util.List<?>) responseBody.get("addresses");

                    if (addressesList != null && addressesList.size() > 0) {
                        log.info("✅ [NaverMapService] addresses 배열 발견, 크기: {}", addressesList.size());

                        @SuppressWarnings("unchecked")
                        java.util.List<Map<String, Object>> addresses = (java.util.List<Map<String, Object>>) addressesList;

                        Map<String, Object> firstAddress = addresses.get(0);
                        String latitudeStr = (String) firstAddress.get("y");
                        String longitudeStr = (String) firstAddress.get("x");

                        if (latitudeStr != null && longitudeStr != null) {
                            try {
                                Double latitude = Double.parseDouble(latitudeStr);
                                Double longitude = Double.parseDouble(longitudeStr);
                                log.info("네이버맵 지오코딩 성공 - 좌표: ({}, {})", latitude, longitude);
                                return new Double[] { latitude, longitude };
                            } catch (NumberFormatException e) {
                                log.warn("좌표 파싱 실패: latitude={}, longitude={}", latitudeStr, longitudeStr);
                                return null;
                            }
                        } else {
                            log.warn("⚠️ [NaverMapService] 좌표 정보가 없습니다 - latitudeStr: {}, longitudeStr: {}",
                                    latitudeStr, longitudeStr);
                            return null;
                        }
                    } else {
                        // addresses가 비어있는 경우
                        log.warn("⚠️ [NaverMapService] 네이버맵 지오코딩 결과 없음 - 주소: {}, status: {}, totalCount: {}",
                                address,
                                responseBody != null ? responseBody.get("status") : "N/A",
                                responseBody != null && responseBody.containsKey("meta")
                                        ? ((Map<?, ?>) responseBody.get("meta")).get("totalCount")
                                        : "N/A");
                        return null;
                    }
                } else {
                    // addresses 키가 없거나 responseBody가 null인 경우
                    log.warn("⚠️ [NaverMapService] 네이버맵 지오코딩 응답에 addresses 키가 없습니다 - 주소: {}", address);
                    return null;
                }
            } else {
                // 응답이 실패하거나 null인 경우
                log.warn("⚠️ [NaverMapService] 네이버맵 지오코딩 실패 - 주소를 찾을 수 없습니다: {}", address);
                return null;
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("네이버맵 지오코딩 API HTTP 에러: {} - 상태: {}", e.getMessage(), e.getStatusCode());
            if (responseBody != null) {
                log.error("응답 본문: {}", responseBody);

                // 401 에러이고 "subscription required" 메시지인 경우
                if (e.getStatusCode() != null && e.getStatusCode().value() == 401 &&
                        (responseBody.contains("subscription") || responseBody.contains("Permission Denied"))) {
                    log.error("네이버맵 Geocoding API 구독이 필요합니다. 네이버 클라우드 플랫폼 콘솔에서 Geocoding API를 구독해주세요.");
                }
            }

            return null;
        } catch (Exception e) {
            log.error("네이버맵 지오코딩 API 호출 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 네이버맵 역지오코딩 (좌표를 주소로 변환)
     * 
     * @param lat 위도
     * @param lng 경도
     * @return 주소 정보
     */
    public Map<String, Object> coordinatesToAddress(double lat, double lng) {
        try {
            // API 키가 없으면 에러 반환
            if (apiKeyId == null || apiKeyId.isEmpty() || apiKey == null || apiKey.isEmpty()) {
                log.warn("네이버맵 API 키가 설정되지 않았습니다.");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "네이버맵 API 키가 설정되지 않았습니다.");
                return errorResponse;
            }

            log.info("네이버맵 역지오코딩 API 호출 - 좌표: ({}, {})", lat, lng);

            // 네이버맵 Geocoding API URL (역지오코딩)
            String url = UriComponentsBuilder
                    .fromUriString("https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc")
                    .queryParam("coords", lng + "," + lat) // 경도,위도 순서
                    .queryParam("output", "json")
                    .toUriString();

            log.debug("요청 URL: {}", url);

            // 헤더 설정 (공식 예시에 따름 - 소문자)
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-ncp-apigw-api-key-id", apiKeyId);
            headers.set("x-ncp-apigw-api-key", apiKey);

            log.debug("요청 헤더 - x-ncp-apigw-api-key-id: {}, x-ncp-apigw-api-key: {}", apiKeyId,
                    apiKey.substring(0, Math.min(5, apiKey.length())) + "***");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // API 호출
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });

            log.info("네이버맵 역지오코딩 API 응답 상태: {}", response.getStatusCode());

            Map<String, Object> result = new HashMap<>();
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                // 네이버맵 역지오코딩 응답 파싱
                if (responseBody != null && responseBody.containsKey("results")) {
                    @SuppressWarnings("unchecked")
                    java.util.List<?> resultsList = (java.util.List<?>) responseBody.get("results");

                    if (resultsList != null && resultsList.size() > 0) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Map<String, Object>> results = (java.util.List<Map<String, Object>>) resultsList;

                        Map<String, Object> firstResult = results.get(0);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> region = (Map<String, Object>) firstResult.get("region");

                        // 주소 조합
                        StringBuilder addressBuilder = new StringBuilder();
                        if (region != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> area1 = (Map<String, String>) region.get("area1"); // 시도
                            @SuppressWarnings("unchecked")
                            Map<String, String> area2 = (Map<String, String>) region.get("area2"); // 시군구
                            @SuppressWarnings("unchecked")
                            Map<String, String> area3 = (Map<String, String>) region.get("area3"); // 읍면동
                            @SuppressWarnings("unchecked")
                            Map<String, String> area4 = (Map<String, String>) region.get("area4"); // 리

                            if (area1 != null && area1.get("name") != null) {
                                addressBuilder.append(area1.get("name"));
                            }
                            if (area2 != null && area2.get("name") != null) {
                                addressBuilder.append(" ").append(area2.get("name"));
                            }
                            if (area3 != null && area3.get("name") != null) {
                                addressBuilder.append(" ").append(area3.get("name"));
                            }
                            if (area4 != null && area4.get("name") != null) {
                                addressBuilder.append(" ").append(area4.get("name"));
                            }
                        }

                        // land 정보 (도로명 주소)
                        @SuppressWarnings("unchecked")
                        Map<String, Object> land = (Map<String, Object>) firstResult.get("land");
                        String roadAddress = null;
                        if (land != null) {
                            roadAddress = (String) land.get("name");
                            String number1 = (String) land.get("number1");
                            String number2 = (String) land.get("number2");

                            if (roadAddress != null) {
                                if (number1 != null) {
                                    roadAddress += " " + number1;
                                }
                                if (number2 != null) {
                                    roadAddress += "-" + number2;
                                }
                            }
                        }

                        result.put("success", true);
                        result.put("address", roadAddress != null ? roadAddress : addressBuilder.toString());
                        result.put("roadAddress", roadAddress);
                        result.put("jibunAddress", addressBuilder.toString());

                        log.info("네이버맵 역지오코딩 성공 - 주소: {}", result.get("address"));
                    } else {
                        result.put("success", false);
                        result.put("message", "주소를 찾을 수 없습니다.");
                    }
                } else {
                    result.put("success", false);
                    result.put("message", "응답에 results 키가 없습니다.");
                }
            } else {
                result.put("success", false);
                result.put("message", "역지오코딩 API 호출 실패");
                result.put("statusCode", response.getStatusCode().value());
            }

            return result;
        } catch (Exception e) {
            log.error("네이버맵 역지오코딩 API 호출 실패: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
}
