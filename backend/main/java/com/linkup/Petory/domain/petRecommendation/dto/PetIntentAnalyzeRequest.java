package com.linkup.Petory.domain.petRecommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
/** NLP 서버에 보내는 의도 분석 요청 DTO. 사용자 입력 텍스트와 반려동물 유형을 담는다. */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PetIntentAnalyzeRequest {

    private String text;
    private String petType;
}
