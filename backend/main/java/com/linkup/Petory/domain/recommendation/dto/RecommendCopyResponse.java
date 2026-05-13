package com.linkup.Petory.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecommendCopyResponse(
        @JsonProperty("request_id") String requestId,
        String recommendation,
        String source,
        @JsonProperty("generated_at") String generatedAt) {
}
