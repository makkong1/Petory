package com.linkup.Petory.domain.petRecommendation.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 반려동물 서비스 추천 시설 응답 DTO. 시설 정보·점수·거리·태그 매칭 이유를 포함한다.
 */
@Getter
@Builder
public class PetRecommendFacilityDto {

    private final Long id;
    private final String name;
    private final String address;
    private final double distanceM;
    private final double rating;
    private final int reviewCount;
    private final double finalScore;
    private final List<String> matchReasons;
    private final List<String> locationTags;
    private final double popularityScore;
}
