package com.linkup.Petory.domain.recommendation.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.recommendation.dto.RecommendRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PetDataApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 생성자
    public PetDataApiClient(
            @Value("${app.pet-data-api.base-url}") @NonNull String baseUrl,
            @Value("${app.pet-data-api.api-key}") String apiKey) {
        log.info("[PetDataApiClient] 초기화 — baseUrl={}", baseUrl);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
        log.info("[PetDataApiClient] 초기화 완료 - restClient={}", restClient);
    }

    @SuppressWarnings("UseSpecificCatch")
    public RecommendResponse recommend(RecommendRequest request) {
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("[PetDataApiClient] 요청 전송 → {}", requestJson);

            @SuppressWarnings("null")
            String responseBody = restClient.post()
                    .uri("/recommend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("[PetDataApiClient] 응답 수신 ← {}", responseBody);
            return objectMapper.readValue(responseBody, RecommendResponse.class);

        } catch (Exception e) {
            log.error("[PetDataApiClient] 실패: {} — {}", e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("추천 API 호출 실패: " + e.getMessage(), e);
        }
    }
}
