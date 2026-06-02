package com.linkup.Petory.domain.petRecommendation.scoring;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendFacilityDto;

/**
 * 추천 후보 시설의 최종 점수를 계산하는 가중치 기반 계산기.
 *
 * <p>
 * 인기도/태그일치/거리/평점/리뷰수를 0~1로 정규화해 가중합 후 0~100 스케일 점수로 반환한다.
 */
@Component
public class PetRecommendScoreCalculator {

    private static final double W_PLACE = 0.35;
    private static final double W_TAG = 0.30;
    private static final double W_DISTANCE = 0.20;
    private static final double W_RATING = 0.10;
    private static final double W_REVIEW = 0.05;

    /**
     * 후보 시설의 최종 추천 점수를 계산한다.
     *
     * <p>인기도·태그일치·거리·평점·리뷰수를 각각 0~1로 정규화한 뒤 가중합하여
     * 0.0~100.0 스케일의 finalScore와 매칭 이유 목록을 담은 DTO를 반환한다.
     *
     * @param dto       점수를 계산할 시설 후보
     * @param radiusM   검색 반경(미터). 거리 점수 정규화 기준으로 사용
     * @param intentTags 사용자 의도 태그 목록. 시설 태그와 교집합 비율로 태그 점수 산출
     */
    public PetRecommendFacilityDto calcScore(
            PetRecommendFacilityDto dto, int radiusM, List<String> intentTags) {

        double placeScore = dto.getPopularityScore();
        double tagScore = calcTagMatchScore(dto.getLocationTags(), intentTags);
        double distanceScore = calcDistanceScore(dto.getDistanceM(), radiusM);
        double ratingScore = calcRatingScore(dto.getRating());
        double reviewScore = calcReviewScore(dto.getReviewCount());

        double rawScore = placeScore * W_PLACE
                + tagScore * W_TAG
                + distanceScore * W_DISTANCE
                + ratingScore * W_RATING
                + reviewScore * W_REVIEW;

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

    /**
     * 거리 점수를 계산한다. 반경 경계에서 0, 중심에서 1인 선형 감소 구조.
     * 반경 초과 시 0 반환.
     */
    private double calcDistanceScore(double distanceM, int radiusM) {
        if (distanceM >= radiusM) {
            return 0.0;
        }
        return 1.0 - (distanceM / radiusM);
    }

    /** 5점 만점 평점을 0~1로 정규화한다. */
    private double calcRatingScore(double rating) {
        return rating / 5.0;
    }

    /**
     * 리뷰 수를 로그 스케일로 0~1 정규화한다.
     * 리뷰 1000개 이상에서 1.0으로 포화되어 과도한 가중치를 방지한다.
     */
    private double calcReviewScore(int reviewCount) {
        if (reviewCount <= 0) {
            return 0.0;
        }
        return Math.min(Math.log10(reviewCount + 1) / Math.log10(1001), 1.0);
    }

    /**
     * 사용자 의도 태그 중 시설 태그와 일치하는 비율을 반환한다.
     * 예: 의도 태그 4개 중 2개 일치 → 0.5
     */
    private double calcTagMatchScore(List<String> locationTags, List<String> intentTags) {
        if (locationTags == null || locationTags.isEmpty()
                || intentTags == null || intentTags.isEmpty()) {
            return 0.0;
        }
        long matched = intentTags.stream().filter(locationTags::contains).count();
        return (double) matched / intentTags.size();
    }

    /**
     * 추천 이유 라벨 목록을 생성한다. 프론트에서 "왜 추천됐는지" 표시에 사용.
     * nearby / high_rating / many_reviews / popular / tag_match:{태그} 조합.
     * 해당 조건이 하나도 없으면 최소 이유로 in_radius를 반환한다.
     */
    private List<String> buildMatchReasons(
            PetRecommendFacilityDto dto, double distanceScore, double ratingScore,
            List<String> intentTags) {
        List<String> reasons = new ArrayList<>();
        if (distanceScore >= 0.7) {
            reasons.add("nearby");
        }
        if (ratingScore >= 0.8) {
            reasons.add("high_rating");
        }
        if (dto.getReviewCount() >= 50) {
            reasons.add("many_reviews");
        }
        if (dto.getPopularityScore() >= 0.5) {
            reasons.add("popular");
        }
        if (intentTags != null && dto.getLocationTags() != null) {
            intentTags.stream()
                    .filter(t -> dto.getLocationTags().contains(t))
                    .map(t -> "tag_match:" + t)
                    .forEach(reasons::add);
        }
        if (reasons.isEmpty()) {
            reasons.add("in_radius");
        }
        return reasons;
    }
}
