package com.linkup.Petory.domain.recommendation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecommendResponse(
                String context,
                @JsonProperty("recommend_version") String recommendVersion,
                @JsonProperty("request_id") String requestId,
                List<FacilityItem> facilities,
                List<TrendItem> trends,
                String recommendation,
                @JsonProperty("generated_at") String generatedAt) {

        public record FacilityItem(
                        Long id,
                        @JsonProperty("source_id") String sourceId,
                        String name,
                        @JsonProperty("distance_m") int distanceM,
                        String address,
                        Double lat,
                        Double lng,
                        @JsonProperty("mention_count") Integer mentionCount,
                        @JsonProperty("mention_score") Double mentionScore,
                        String source,
                        Double score,
                        List<String> reasons) {
        }

        public record TrendItem(
                        String keyword,
                        double score) {
        }
}
