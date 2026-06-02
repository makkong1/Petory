package com.linkup.Petory.domain.petRecommendation.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 반려동물 서비스 추천 최종 응답 DTO. 의도 분석 결과와 추천 시설 목록을 포함한다. */
@Getter
@Builder
public class PetRecommendResponse {

    private String requestText;
    private PetIntentAnalyzeResponse analysis;
    private String message;
    private List<PetRecommendFacilityDto> facilities;
}
