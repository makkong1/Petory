package com.linkup.Petory.domain.petRecommendation.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class PetRecommendResponse {
    private String requestText;
    private PetIntentAnalyzeResponse analysis;
    private String message;
    private List<PetRecommendFacilityDto> facilities;
}
