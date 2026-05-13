package com.linkup.Petory.domain.recommendation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrendTimeseriesResponse(
        String category,
        int days,
        @JsonProperty("top_keywords") int topKeywords,
        List<Point> points) {

    public record Point(
            String date,
            String keyword,
            double score) {
    }
}
