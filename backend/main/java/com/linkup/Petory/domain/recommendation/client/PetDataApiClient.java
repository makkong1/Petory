package com.linkup.Petory.domain.recommendation.client;

import com.linkup.Petory.domain.recommendation.dto.RecommendRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PetDataApiClient {

    private final RestClient restClient;

    public PetDataApiClient(
            @Value("${pet-data-api.base-url}") String baseUrl,
            @Value("${pet-data-api.api-key}") String apiKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    public RecommendResponse recommend(RecommendRequest request) {
        return restClient.post()
                .uri("/recommend")
                .body(request)
                .retrieve()
                .body(RecommendResponse.class);
    }
}
