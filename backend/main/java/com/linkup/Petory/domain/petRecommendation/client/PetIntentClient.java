package com.linkup.Petory.domain.petRecommendation.client;

import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeRequest;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class PetIntentClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PetIntentClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.pet-intent.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.pet-intent.timeout-ms:3000}") long timeoutMs) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public Optional<PetIntentAnalyzeResponse> analyze(String text, String petType) {
        try {
            PetIntentAnalyzeRequest req = PetIntentAnalyzeRequest.builder()
                    .text(text)
                    .petType(petType)
                    .build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PetIntentAnalyzeRequest> entity = new HttpEntity<>(req, headers);

            ResponseEntity<PetIntentAnalyzeResponse> resp = restTemplate.postForEntity(
                    baseUrl + "/api/pet-intent/analyze",
                    entity,
                    PetIntentAnalyzeResponse.class
            );
            return Optional.ofNullable(resp.getBody());
        } catch (RestClientException e) {
            log.warn("[PetIntentClient] Python 서버 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
