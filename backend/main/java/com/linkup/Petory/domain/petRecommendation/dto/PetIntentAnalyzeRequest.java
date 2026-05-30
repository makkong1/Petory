package com.linkup.Petory.domain.petRecommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PetIntentAnalyzeRequest {
    private String text;
    private String petType;
}
