package com.linkup.Petory.domain.petRecommendation.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter @Builder
public class UserPetIntentSignalResponse {
    private String intentDomain;
    private String intent;
    private List<String> recommendedCategories;
    private Double confidence;
    private List<String> intentTags;
    private String cardMessage;
    private String actionLabel;
    private String targetTab;
    private String targetCategory;
}
