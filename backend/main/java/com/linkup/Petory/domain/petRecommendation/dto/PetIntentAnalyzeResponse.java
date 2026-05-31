package com.linkup.Petory.domain.petRecommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
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
