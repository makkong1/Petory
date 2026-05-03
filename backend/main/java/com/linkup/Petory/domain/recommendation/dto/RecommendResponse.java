package com.linkup.Petory.domain.recommendation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecommendResponse(
                String context,
                List<FacilityItem> facilities,
                List<TrendItem> trends,
                String recommendation,
                @JsonProperty("generated_at") String generatedAt) {

        public record FacilityItem(
                        String name,
                        @JsonProperty("distance_m") int distanceM,
                        String address,
                        Double lat,
                        Double lng) {
        }

        public record TrendItem(
                        String keyword,
                        double score) {
        }
}
