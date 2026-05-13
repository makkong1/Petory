package com.linkup.Petory.domain.recommendation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendEventRequest(
        @JsonProperty("request_id") String requestId,
        List<Event> events) {

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Event(
            @JsonProperty("facility_id") Long facilityId,
            @JsonProperty("source_id") String sourceId,
            String event,
            @JsonProperty("occurred_at") String occurredAt,
            @JsonProperty("user_ref") String userRef) {
    }
}
