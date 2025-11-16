package com.linkup.Petory.domain.location.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationServiceLoadResponse {

    private final String message;
    private final String region;
    private final List<String> keywords;
    private final int keywordCount;
    private final int maxResultsPerKeyword;
    private final int totalLimit;
    private final int fetchedCount;
    private final int savedCount;
    private final int duplicateCount;
    private final int skippedCount;
}

