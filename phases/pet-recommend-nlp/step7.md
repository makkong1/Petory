# Step 7: PetRecommendScoreCalculator + fallback 처리

## 목표
추천 점수를 계산하는 `PetRecommendScoreCalculator`를 구현하고,
`PetRecommendationService`에 연결한다.
Python 서버 장애 시 키워드 기반 fallback도 완성한다.

## 배경
- Step 6의 `PetRecommendationService`에서 `finalScore = 0.0` 으로 남겨둔 부분을 채움
- 점수 공식 (아키텍처 문서 Section 9):
  ```
  final_score = place_score * 0.35 + tag_match_score * 0.30 + distance_score * 0.20 + rating_score * 0.10 + review_score * 0.05
  ```
- **Phase 3 기준**: `place_score = 0`, `tag_match_score = 0` (Phase 4 전까지)
  - 실질적으로 `distance_score * 0.20 + rating_score * 0.10 + review_score * 0.05`만 사용
- `distance_score`: 반경 내 거리 기반 정규화 (가까울수록 높음)
- `rating_score`: 0~5 평점 → 0~100 정규화
- `review_score`: 리뷰 수 → 로그 정규화

## 생성할 파일

### `backend/main/java/com/linkup/Petory/domain/petRecommendation/scoring/PetRecommendScoreCalculator.java`

```java
package com.linkup.Petory.domain.petRecommendation.scoring;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendFacilityDto;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class PetRecommendScoreCalculator {

    private static final double W_PLACE    = 0.35;
    private static final double W_TAG      = 0.30;
    private static final double W_DISTANCE = 0.20;
    private static final double W_RATING   = 0.10;
    private static final double W_REVIEW   = 0.05;

    public PetRecommendFacilityDto calcScore(
            PetRecommendFacilityDto dto, int radiusM, List<String> intentTags) {

        double placeScore    = 0.0;  // Phase 4 전까지 0
        double tagScore      = 0.0;  // Phase 4 전까지 0
        double distanceScore = calcDistanceScore(dto.getDistanceM(), radiusM);
        double ratingScore   = calcRatingScore(dto.getRating());
        double reviewScore   = calcReviewScore(dto.getReviewCount());

        double finalScore = placeScore    * W_PLACE
                          + tagScore      * W_TAG
                          + distanceScore * W_DISTANCE
                          + ratingScore   * W_RATING
                          + reviewScore   * W_REVIEW;

        List<String> matchReasons = buildMatchReasons(dto, distanceScore, ratingScore);

        return PetRecommendFacilityDto.builder()
                .id(dto.getId())
                .name(dto.getName())
                .address(dto.getAddress())
                .distanceM(dto.getDistanceM())
                .rating(dto.getRating())
                .reviewCount(dto.getReviewCount())
                .finalScore(Math.round(finalScore * 1000.0) / 10.0)  // 0~100 스케일
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

    private List<String> buildMatchReasons(
            PetRecommendFacilityDto dto, double distanceScore, double ratingScore) {
        List<String> reasons = new ArrayList<>();
        if (distanceScore >= 0.7) reasons.add("nearby");
        if (ratingScore >= 0.8)  reasons.add("high_rating");
        if (dto.getReviewCount() >= 50) reasons.add("many_reviews");
        if (reasons.isEmpty())   reasons.add("in_radius");
        return reasons;
    }
}
```

### `PetRecommendationService.java` 수정 — Step 6 파일에 아래 변경 적용

1. `PetRecommendScoreCalculator` 주입 추가:
```java
private final PetRecommendScoreCalculator scoreCalculator;
```

2. `toDto()` 호출 이후 점수 계산 적용:
```java
List<PetRecommendFacilityDto> facilities = nearby.stream()
        .limit(10)
        .map(loc -> toDto(loc, lat, lng, analysis.getIntentTags()))
        .map(dto -> scoreCalculator.calcScore(dto, radius, analysis.getIntentTags()))
        .sorted(Comparator.comparingDouble(PetRecommendFacilityDto::getFinalScore).reversed())
        .toList();
```

3. `fallbackRecommend()` 구현 완성:
```java
private PetRecommendResponse fallbackRecommend(
        String text, double lat, double lng, int radius) {
    log.warn("[PetRecommendationService] Python 서버 장애 — fallback 실행. text={}", text);

    String fallbackCategory = inferCategoryFromKeyword(text);
    List<LocationService> nearby = locationServiceRepository
            .findNearbyByCategory(lat, lng, radius, fallbackCategory);

    List<PetRecommendFacilityDto> facilities = nearby.stream()
            .limit(10)
            .map(loc -> toDto(loc, lat, lng, List.of()))
            .toList();

    return PetRecommendResponse.builder()
            .requestText(text)
            .analysis(null)
            .message("현재 반려생활 의도 분석 서버가 응답하지 않아 기본 검색 결과를 제공합니다.")
            .facilities(facilities)
            .build();
}

private String inferCategoryFromKeyword(String text) {
    if (text.contains("병원") || text.contains("약국") || text.contains("아파") || text.contains("긁")) return "동물병원";
    if (text.contains("미용") || text.contains("털") || text.contains("목욕")) return "미용";
    if (text.contains("카페")) return "카페";
    if (text.contains("호텔") || text.contains("펜션")) return "호텔";
    return "동물병원";  // 기본값
}
```

## Acceptance Criteria

```bash
# 1. 컴파일
cd /Users/maknkkong/project/Petory
./gradlew compileJava
# 기대: BUILD SUCCESSFUL

# 2. 정상 흐름: Python + Spring 실행 후
curl "http://localhost:8080/api/pet-recommend?lat=37.501&lng=127.039&text=우리%20강아지가%20귀를%20자꾸%20긁어요&radius=3000" \
  -H "Authorization: Bearer <token>"
# 기대: facilities[0].finalScore > 0, matchReasons 포함

# 3. fallback 테스트: Python 서버 종료 후
# pkill -f uvicorn  (또는 서버 중단)
curl "http://localhost:8080/api/pet-recommend?lat=37.501&lng=127.039&text=강아지%20병원&radius=3000" \
  -H "Authorization: Bearer <token>"
# 기대: 500 아님, message에 "분석 서버가 응답하지 않아" 문구 포함
```
