package com.linkup.Petory.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendRequest(
        double lat,
        double lng,
        String context,
        @JsonProperty("radius_km") double radiusKm,
        @JsonProperty("top_n") int topN,
        PetInfo pet
) {
    @Builder
    public record PetInfo(
            String type,
            String breed,
            @JsonProperty("age_months") Integer ageMonths
    ) {}
}
