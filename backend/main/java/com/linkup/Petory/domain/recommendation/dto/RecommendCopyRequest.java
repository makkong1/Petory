package com.linkup.Petory.domain.recommendation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendCopyRequest(
        String context,
        @JsonProperty("request_id") String requestId,
        List<CopyFacility> facilities,
        List<TrendItem> trends,
        RecommendRequest.PetInfo pet) {

    @Builder
    public record CopyFacility(
            String name,
            @JsonProperty("distance_m") int distanceM) {
    }

    @Builder
    public record TrendItem(
            String keyword,
            double score) {
    }
}
