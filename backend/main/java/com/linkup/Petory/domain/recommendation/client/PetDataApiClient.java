package com.linkup.Petory.domain.recommendation.client;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.recommendation.dto.RecommendCopyRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendCopyResponse;
import com.linkup.Petory.domain.recommendation.dto.RecommendEventRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;
import com.linkup.Petory.domain.recommendation.dto.TrendTimeseriesResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PetDataApiClient {

    private final RestClient recommendClient;
    private final RestClient copyClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetDataApiClient(
            @Value("${app.pet-data-api.base-url}") @NonNull String baseUrl,
            @Value("${app.pet-data-api.api-key}") String apiKey,
            @Value("${app.pet-data-api.timeout-ms:3000}") int timeoutMs,
            @Value("${app.pet-data-api.copy-timeout-ms:35000}") int copyTimeoutMs) {
        log.info("[PetDataApiClient] 초기화 — baseUrl={}, timeoutMs={}, copyTimeoutMs={}",
                baseUrl, timeoutMs, copyTimeoutMs);

        // v3: 본 추천(/recommend)은 LLM 미호출. 빠른 타임아웃.
        this.recommendClient = buildClient(baseUrl, apiKey, timeoutMs);
        // v3: /recommend/copy 는 Ollama 동기 대기. 길게.
        this.copyClient = buildClient(baseUrl, apiKey, copyTimeoutMs);
    }

    private RestClient buildClient(String baseUrl, String apiKey, int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .requestFactory(factory)
                .build();
    }

    @SuppressWarnings("UseSpecificCatch")
    public RecommendResponse recommend(RecommendRequest request) {
        // 응답의 request_id 와 동일하게 매핑되어 추후 /recommend/copy 와 /events/recommendation 에서 재사용.
        String requestId = newRequestId();
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("[PetDataApiClient/recommend] 요청 전송 reqId={} → {}", requestId, requestJson);

            @SuppressWarnings("null")
            String responseBody = recommendClient.post()
                    .uri("/recommend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", requestId)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("[PetDataApiClient/recommend] 응답 수신 reqId={} ← {}", requestId, responseBody);
            return objectMapper.readValue(responseBody, RecommendResponse.class);

        } catch (Exception e) {
            log.error("[PetDataApiClient/recommend] 실패 reqId={} {} — {}",
                    requestId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("추천 API 호출 실패: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public RecommendCopyResponse recommendCopy(RecommendCopyRequest request) {
        // 카피는 본 추천의 request_id 를 그대로 넘겨서 서버 로그상 1콜로 묶이도록 한다.
        String requestId = request.requestId() != null ? request.requestId() : newRequestId();
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("[PetDataApiClient/copy] 요청 전송 reqId={} → {}", requestId, requestJson);

            @SuppressWarnings("null")
            String responseBody = copyClient.post()
                    .uri("/recommend/copy")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", requestId)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("[PetDataApiClient/copy] 응답 수신 reqId={} ← {}", requestId, responseBody);
            return objectMapper.readValue(responseBody, RecommendCopyResponse.class);

        } catch (Exception e) {
            log.error("[PetDataApiClient/copy] 실패 reqId={} {} — {}",
                    requestId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("추천 카피 API 호출 실패: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public void sendEvents(RecommendEventRequest request) {
        // 가이드 §6: 응답은 항상 202 Accepted. fire-and-forget — 실패해도 사용자 액션을 막지 않는다.
        String requestId = newRequestId();
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("[PetDataApiClient/events] 요청 전송 reqId={} → {}", requestId, requestJson);

            recommendClient.post()
                    .uri("/events/recommendation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", requestId)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[PetDataApiClient/events] 응답 수신 reqId={}", requestId);
        } catch (Exception e) {
            log.warn("[PetDataApiClient/events] 실패(무시) reqId={} {} — {}",
                    requestId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public TrendTimeseriesResponse getTrendTimeseries(String category, int days, int topKeywords) {
        // /trends/.../timeseries 는 Postgres 조회로 빠르다. 본 추천과 같은 3s 타임아웃 클라이언트 재사용.
        String requestId = newRequestId();
        try {
            log.info("[PetDataApiClient/trends] 요청 전송 reqId={} category={} days={} top_keywords={}",
                    requestId, category, days, topKeywords);

            @SuppressWarnings("null")
            String responseBody = recommendClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/trends/{category}/timeseries")
                            .queryParam("days", days)
                            .queryParam("top_keywords", topKeywords)
                            .build(category))
                    .header("X-Request-Id", requestId)
                    .retrieve()
                    .body(String.class);

            log.info("[PetDataApiClient/trends] 응답 수신 reqId={} bytes={}",
                    requestId, responseBody == null ? 0 : responseBody.length());
            return objectMapper.readValue(responseBody, TrendTimeseriesResponse.class);

        } catch (Exception e) {
            log.error("[PetDataApiClient/trends] 실패 reqId={} {} — {}",
                    requestId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("트렌드 시계열 API 호출 실패: " + e.getMessage(), e);
        }
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
