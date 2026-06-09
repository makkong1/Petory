package com.linkup.Petory.domain.petRecommendation.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 사용자 펫 의도 신호 응답 DTO. 저장된 의도 도메인·의도·태그·신뢰도·생성 시각을 포함한다. */
@Getter
@Builder
public class UserPetIntentSignalResponse {

    private String intentDomain;
    private String intent;
    private List<String> recommendedCategories;
    private Double confidence;
    private String urgency;
    private List<String> intentTags;
    private String cardMessage;
    private String actionLabel;
    private String targetTab;
    private String targetCategory;
}
