package com.linkup.Petory.domain.petRecommendation.service;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendFacilityDto;
import com.linkup.Petory.domain.petRecommendation.scoring.PetRecommendScoreCalculator;

class PetRecommendScoreCalculatorTest {

    private final PetRecommendScoreCalculator calculator = new PetRecommendScoreCalculator();

    @Test
    @DisplayName("가중합 점수와 추천 이유를 계산한다")
    void calcScore_calculatesWeightedScoreAndReasons() {
        PetRecommendFacilityDto dto = PetRecommendFacilityDto.builder()
                .id(1L)
                .name("해피 동물병원")
                .address("서울")
                .distanceM(250.0)
                .rating(4.5)
                .reviewCount(100)
                .popularityScore(0.8)
                .locationTags(List.of("병원", "24시", "주차"))
                .build();

        PetRecommendFacilityDto result = calculator.calcScore(
                dto,
                1000,
                List.of("병원", "미용", "24시", "카페"));

        assertThat(result.getFinalScore()).isBetween(70.3, 70.4);
        assertThat(result.getMatchReasons())
                .contains("nearby", "high_rating", "many_reviews", "popular",
                        "tag_match:병원", "tag_match:24시");
    }

    @Test
    @DisplayName("최종 점수 내림차순으로 추천 후보를 정렬할 수 있다")
    void calcScore_supportsRankingByFinalScoreDesc() {
        List<String> intentTags = List.of("병원", "24시");
        List<PetRecommendFacilityDto> candidates = List.of(
                facility(1L, "낮은 점수", 900, 3.0, 5, 0.1, List.of("카페")),
                facility(2L, "높은 점수", 100, 4.8, 120, 0.9, List.of("병원", "24시")),
                facility(3L, "중간 점수", 300, 4.0, 30, 0.4, List.of("병원")));

        List<PetRecommendFacilityDto> ranked = candidates.stream()
                .map(candidate -> calculator.calcScore(candidate, 1000, intentTags))
                .sorted(Comparator.comparingDouble(PetRecommendFacilityDto::getFinalScore).reversed())
                .toList();

        assertThat(ranked).extracting(PetRecommendFacilityDto::getName)
                .containsExactly("높은 점수", "중간 점수", "낮은 점수");
    }

    @Test
    @DisplayName("태그가 없으면 태그 점수 없이도 최소 추천 이유를 제공한다")
    void calcScore_withoutTags_keepsFallbackReason() {
        PetRecommendFacilityDto dto = facility(
                1L, "기본 후보", 990, 1.0, 0, 0.0, List.of());

        PetRecommendFacilityDto result = calculator.calcScore(dto, 1000, List.of("병원"));

        assertThat(result.getFinalScore()).isGreaterThan(0.0);
        assertThat(result.getMatchReasons()).containsExactly("in_radius");
    }

    private PetRecommendFacilityDto facility(
            Long id,
            String name,
            double distanceM,
            double rating,
            int reviewCount,
            double popularityScore,
            List<String> locationTags) {
        return PetRecommendFacilityDto.builder()
                .id(id)
                .name(name)
                .address("서울")
                .distanceM(distanceM)
                .rating(rating)
                .reviewCount(reviewCount)
                .popularityScore(popularityScore)
                .locationTags(locationTags)
                .build();
    }
}
