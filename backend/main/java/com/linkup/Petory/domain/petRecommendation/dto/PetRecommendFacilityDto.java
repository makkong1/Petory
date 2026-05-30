package com.linkup.Petory.domain.petRecommendation.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

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
