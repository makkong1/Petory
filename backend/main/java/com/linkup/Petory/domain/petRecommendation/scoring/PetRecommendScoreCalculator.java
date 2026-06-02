package com.linkup.Petory.domain.petRecommendation.scoring;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendFacilityDto;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * 추천 후보 시설의 최종 점수를 계산하는 가중치 기반 계산기.
 *
 * <p>인기도/태그일치/거리/평점/리뷰수를 0~1로 정규화해 가중합 후 0~100 스케일 점수로 반환한다.
 */
@Component
public class PetRecommendScoreCalculator {

    private static final double W_PLACE    = 0.35;
    private static final double W_TAG      = 0.30;
    private static final double W_DISTANCE = 0.20;
    private static final double W_RATING   = 0.10;
    private static final double W_REVIEW   = 0.05;

    public PetRecommendFacilityDto calcScore(
            PetRecommendFacilityDto dto, int radiusM, List<String> intentTags) {

        double placeScore    = dto.getPopularityScore();
        double tagScore      = calcTagMatchScore(dto.getLocationTags(), intentTags);
        double distanceScore = calcDistanceScore(dto.getDistanceM(), radiusM);
        double ratingScore   = calcRatingScore(dto.getRating());
        double reviewScore   = calcReviewScore(dto.getReviewCount());

        double rawScore = placeScore    * W_PLACE
                        + tagScore      * W_TAG
                        + distanceScore * W_DISTANCE
                        + ratingScore   * W_RATING
                        + reviewScore   * W_REVIEW;

        double finalScore = Math.round(rawScore * 1000.0) / 10.0;

        List<String> matchReasons = buildMatchReasons(dto, distanceScore, ratingScore, intentTags);

        return PetRecommendFacilityDto.builder()
                .id(dto.getId())
                .name(dto.getName())
                .address(dto.getAddress())
                .distanceM(dto.getDistanceM())
                .rating(dto.getRating())
                .reviewCount(dto.getReviewCount())
                .popularityScore(placeScore)
                .locationTags(dto.getLocationTags())
                .finalScore(finalScore)
                .matchReasons(matchReasons)
                .build();
    }

    private double calcDistanceScore(double distanceM, int radiusM) {
        if (distanceM >= radiusM) return 0.0;
        return 1.0 - (distanceM / radiusM);
    }

    private double calcRatingScore(double rating) {
        return rating / 5.0;
    }

    private double calcReviewScore(int reviewCount) {
        if (reviewCount <= 0) return 0.0;
        return Math.min(Math.log10(reviewCount + 1) / Math.log10(1001), 1.0);
    }

    private double calcTagMatchScore(List<String> locationTags, List<String> intentTags) {
        if (locationTags == null || locationTags.isEmpty()
                || intentTags == null || intentTags.isEmpty()) return 0.0;
        long matched = intentTags.stream().filter(locationTags::contains).count();
        return (double) matched / intentTags.size();
    }

    private List<String> buildMatchReasons(
            PetRecommendFacilityDto dto, double distanceScore, double ratingScore,
            List<String> intentTags) {
        List<String> reasons = new ArrayList<>();
        if (distanceScore >= 0.7)           reasons.add("nearby");
        if (ratingScore   >= 0.8)           reasons.add("high_rating");
        if (dto.getReviewCount() >= 50)     reasons.add("many_reviews");
        if (dto.getPopularityScore() >= 0.5) reasons.add("popular");
        if (intentTags != null && dto.getLocationTags() != null) {
            intentTags.stream()
                    .filter(t -> dto.getLocationTags().contains(t))
                    .map(t -> "tag_match:" + t)
                    .forEach(reasons::add);
        }
        if (reasons.isEmpty()) reasons.add("in_radius");
        return reasons;
    }
}
