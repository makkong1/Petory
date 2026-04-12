package com.linkup.Petory.domain.location.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 네이버맵 API 서비스
 */
@Slf4j
@Service
public class NaverMapService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Value("${naver.map.api.client-id:}")
    private String apiKeyId;

    @Value("${naver.map.api.client-secret:}")
    private String apiKey;

    private final RestClient restClient;

    public NaverMapService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

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

            Map<String, Object> responseBody = restClient.get()
                    .uri(url)
                    .headers(h -> {
                        h.set("X-NCP-APIGW-API-KEY-ID", apiKeyId);
                        h.set("X-NCP-APIGW-API-KEY", apiKey);
                    })
                    .retrieve()
                    .body(MAP_TYPE);

            Map<String, Object> result = new HashMap<>();
            if (responseBody != null) {
                result.put("success", true);
                result.put("data", responseBody);
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
            } else {
                result.put("success", false);
                result.put("message", "길찾기 API 응답 본문이 비어 있습니다.");
                log.warn("네이버맵 Directions API 응답 본문 null");
            }

            return result;
        } catch (RestClientResponseException e) {
            String errBody = e.getResponseBodyAsString();
            log.warn("네이버맵 Directions API HTTP 에러: {} - 상태: {}, 응답: {}", e.getMessage(), e.getStatusCode(),
                    errBody);

            if (e.getStatusCode().value() == 401
                    && errBody != null && errBody.contains("subscription")) {
                log.warn("네이버맵 Directions API 구독이 필요합니다. 웹 URL 방식은 정상 작동합니다.");
            }

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("message", "네이버맵 Directions API 구독이 필요합니다. 웹 URL 방식은 정상 작동합니다.");
            errorResponse.put("responseBody", errBody);
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

            // 주소 정리: + 문자를 공백으로 변환하고 공백을 하나로 통일
            String cleanedAddress = address.replace("+", " ").replaceAll("\\s+", " ").trim();

            // 네이버맵 Geocoding API URL (지오코딩) - 공식 문서에 따름
            // 공식 엔드포인트: https://maps.apigw.ntruss.com/map-geocode/v2/geocode
            String url = UriComponentsBuilder
                    .fromUriString("https://maps.apigw.ntruss.com/map-geocode/v2/geocode")
                    .queryParam("query", cleanedAddress)
                    .encode() // URL 인코딩 자동 처리
                    .toUriString();

            log.info("📡 [NaverMapService] API 호출 시작...");
            Map<String, Object> responseBody = restClient.get()
                    .uri(url)
                    .headers(h -> {
                        h.set("X-NCP-APIGW-API-KEY-ID", apiKeyId);
                        h.set("X-NCP-APIGW-API-KEY", apiKey);
                        h.set("Accept", "application/json");
                    })
                    .retrieve()
                    .body(MAP_TYPE);

            if (responseBody != null) {
                // 네이버맵 지오코딩 응답 파싱
                if (responseBody.containsKey("addresses")) {
                    List<?> addressesList = (List<?>) responseBody.get("addresses");

                    if (addressesList != null && addressesList.size() > 0) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> addresses = (List<Map<String, Object>>) addressesList;

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
                    log.warn("⚠️ [NaverMapService] 네이버맵 지오코딩 응답에 addresses 키가 없습니다 - 주소: {}", address);
                    return null;
                }
            } else {
                log.warn("⚠️ [NaverMapService] 네이버맵 지오코딩 실패 - 응답 본문 없음: {}", address);
                return null;
            }
        } catch (RestClientResponseException e) {
            String errBody = e.getResponseBodyAsString();
            log.error("네이버맵 지오코딩 API HTTP 에러: {} - 상태: {}", e.getMessage(), e.getStatusCode());
            if (errBody != null) {
                log.error("응답 본문: {}", errBody);

                if (e.getStatusCode().value() == 401
                        && (errBody.contains("subscription") || errBody.contains("Permission Denied"))) {
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
     * 네이버맵 주소 검색 (키워드 → 주소 목록 반환)
     *
     * @param query 검색어 (주소 또는 장소명 일부)
     * @return 주소 결과 목록, 각 항목에 address/roadAddress/latitude/longitude 포함
     */
    public List<Map<String, Object>> searchAddresses(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        if (apiKeyId == null || apiKeyId.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            log.warn("네이버맵 API 키가 설정되지 않았습니다.");
            return List.of();
        }

        try {
            String cleanedQuery = query.replace("+", " ").replaceAll("\\s+", " ").trim();

            String url = UriComponentsBuilder
                    .fromUriString("https://maps.apigw.ntruss.com/map-geocode/v2/geocode")
                    .queryParam("query", cleanedQuery)
                    .encode()
                    .toUriString();

            Map<String, Object> responseBody = restClient.get()
                    .uri(url)
                    .headers(h -> {
                        h.set("X-NCP-APIGW-API-KEY-ID", apiKeyId);
                        h.set("X-NCP-APIGW-API-KEY", apiKey);
                        h.set("Accept", "application/json");
                    })
                    .retrieve()
                    .body(MAP_TYPE);

            if (responseBody == null || !responseBody.containsKey("addresses")) {
                return List.of();
            }

            List<?> addressesList = (List<?>) responseBody.get("addresses");
            if (addressesList == null || addressesList.isEmpty()) {
                return List.of();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> addresses = (List<Map<String, Object>>) addressesList;

            return addresses.stream()
                    .limit(5)
                    .map(addr -> {
                        Map<String, Object> result = new HashMap<>();
                        String roadAddr = (String) addr.get("roadAddress");
                        String jibunAddr = (String) addr.get("jibunAddress");
                        String display = (roadAddr != null && !roadAddr.isBlank()) ? roadAddr : jibunAddr;
                        result.put("address", display);
                        result.put("roadAddress", roadAddr);
                        result.put("jibunAddress", jibunAddr);
                        try {
                            result.put("latitude", Double.parseDouble((String) addr.get("y")));
                            result.put("longitude", Double.parseDouble((String) addr.get("x")));
                        } catch (NumberFormatException e) {
                            log.warn("좌표 파싱 실패: {}", addr);
                        }
                        return result;
                    })
                    .filter(r -> r.containsKey("latitude"))
                    .collect(Collectors.toList());

        } catch (RestClientResponseException e) {
            log.error("네이버맵 주소 검색 API HTTP 에러: {} - 상태: {}", e.getMessage(), e.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.error("네이버맵 주소 검색 실패: {}", e.getMessage(), e);
            return List.of();
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

            // 네이버맵 Reverse Geocoding API URL (공식 문서 기준)
            String url = UriComponentsBuilder
                    .fromUriString("https://maps.apigw.ntruss.com/map-reversegeocode/v2/gc")
                    .queryParam("coords", lng + "," + lat) // 경도,위도 순서
                    .queryParam("output", "json")
                    .queryParam("orders", "legalcode,admcode,addr,roadaddr") // 공식 문서 예시에 따라 추가
                    .toUriString();

            log.info("🌐 [역지오코딩] 요청 URL: {}", url);

            Map<String, Object> responseBody = restClient.get()
                    .uri(url)
                    .headers(h -> {
                        h.set("X-NCP-APIGW-API-KEY-ID", apiKeyId);
                        h.set("X-NCP-APIGW-API-KEY", apiKey);
                    })
                    .retrieve()
                    .body(MAP_TYPE);

            log.info("네이버맵 역지오코딩 API 응답 수신");

            Map<String, Object> result = new HashMap<>();
            if (responseBody != null) {
                // 네이버맵 역지오코딩 응답 파싱
                if (responseBody.containsKey("results")) {
                    List<?> resultsList = (List<?>) responseBody.get("results");

                    if (resultsList != null && resultsList.size() > 0) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> results = (List<Map<String, Object>>) resultsList;

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
                result.put("message", "역지오코딩 API 응답 본문이 비어 있습니다.");
            }

            return result;
        } catch (RestClientResponseException e) {
            String errBody = e.getResponseBodyAsString();
            log.error("네이버맵 역지오코딩 API HTTP 에러: {} - 상태: {}, 응답: {}", e.getMessage(), e.getStatusCode(), errBody);

            if (e.getStatusCode().value() == 401
                    && errBody != null
                    && (errBody.contains("subscription") || errBody.contains("Permission Denied"))) {
                log.error("네이버맵 역지오코딩 API 구독이 필요합니다. 네이버 클라우드 플랫폼 콘솔에서 Reverse Geocoding API를 구독해주세요.");
            }

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("message", "네이버맵 역지오코딩 API 구독이 필요합니다. 네이버 클라우드 플랫폼 콘솔에서 Reverse Geocoding API를 구독해주세요.");
            errorResponse.put("responseBody", errBody);
            return errorResponse;
        } catch (Exception e) {
            log.error("네이버맵 역지오코딩 API 호출 실패: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
}
