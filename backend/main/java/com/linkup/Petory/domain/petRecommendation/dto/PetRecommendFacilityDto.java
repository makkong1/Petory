package com.linkup.Petory.domain.petRecommendation.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 반려동물 서비스 추천 시설 응답 DTO. 시설 정보·점수·거리·태그 매칭 이유를 포함한다. */
@Getter
@Builder
public class PetRecommendFacilityDto {

    private Long id;
    private String name;
    private String address;
    private double distanceM;
    private double rating;
    private int reviewCount;
    private double finalScore;
    private List<String> matchReasons;
    private List<String> locationTags;
    private double popularityScore;
}
