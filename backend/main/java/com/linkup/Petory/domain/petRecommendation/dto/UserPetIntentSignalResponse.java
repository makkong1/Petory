package com.linkup.Petory.domain.petRecommendation.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 사용자 펫 의도 신호 응답 DTO. 저장된 의도 도메인·의도·태그·신뢰도·생성 시각을 포함한다.
 */
@Getter
@Builder
public class UserPetIntentSignalResponse {

    private final String intentDomain;
    private final String intent;
    private final List<String> recommendedCategories;
    private final Double confidence;
    private final String urgency;
    private final List<String> intentTags;
    private final String cardMessage;
    private final String actionLabel;
    private final String targetTab;
    private final String targetCategory;
}
