package com.linkup.Petory.domain.petRecommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
/** NLP 서버로부터 받는 의도 분석 결과 DTO. 의도 도메인·의도·태그 목록을 포함한다. */
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PetIntentAnalyzeResponse {

    private String intentDomain;
    private String intent;
    private List<String> recommendedCategories;
    private double confidence;
    private List<String> keywords;
    private List<String> intentTags;
    private String urgency;
    private String message;
    private List<String> suggestedCategories;
}
