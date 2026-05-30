# Step 6: PetRecommendationService + Controller + LocationService 반경 검색 연동

## 목표
Spring에서 사용자 입력을 받아 Python 서버를 호출하고,
`LocationService` DB에서 반경 검색 결과와 조합해 추천 응답을 반환하는
`PetRecommendationService`와 `PetRecommendationController`를 구현한다.

## 배경
- Step 5에서 만든 `PetIntentClient` 사용
- `LocationService` 엔티티/리포지토리는 `domain/location/`에 이미 존재
- `LocationService` 테이블 컬럼: `id`, `name`, `address`, `category1`, `category2`, `category3`, `lat`, `lng`, `rating`, `reviewCount` 등 확인 후 맞춰서 사용
- 반경 검색 쿼리: `ST_Distance_Sphere(point(lng, lat), point(:lng, :lat)) <= :radius` (기존 쿼리 재사용)
- 엔드포인트: `GET /api/pet-recommend`
- 인증 필요: `@PreAuthorize("hasRole('USER')")`

## 확인할 기존 파일
- `domain/location/entity/LocationService.java` — 필드명 확인
- `domain/location/repository/LocationServiceRepository.java` — 반경 검색 쿼리 메서드 확인
- 기존 반경 검색 메서드가 있으면 재사용, 없으면 JPQL로 추가

## 생성할 파일

### `backend/main/java/com/linkup/Petory/domain/petRecommendation/dto/PetRecommendFacilityDto.java`

```java
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
}
```

### `backend/main/java/com/linkup/Petory/domain/petRecommendation/dto/PetRecommendResponse.java`

```java
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
```

### `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetRecommendationService.java`

```java
package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.petRecommendation.client.PetIntentClient;
import com.linkup.Petory.domain.petRecommendation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetRecommendationService {

    private final PetIntentClient petIntentClient;
    private final LocationServiceRepository locationServiceRepository;

    public PetRecommendResponse recommend(
            String text, double lat, double lng, int radius, String petType) {

        // 1. Python NLP 서버 호출
        Optional<PetIntentAnalyzeResponse> analysisOpt = petIntentClient.analyze(text, petType);

        if (analysisOpt.isEmpty()) {
            return fallbackRecommend(text, lat, lng, radius);
        }

        PetIntentAnalyzeResponse analysis = analysisOpt.get();
        List<String> categories = analysis.getRecommendedCategories();

        // 2. confidence 낮으면 카테고리 선택 유도
        if (categories == null || categories.isEmpty()) {
            return PetRecommendResponse.builder()
                    .requestText(text)
                    .analysis(analysis)
                    .message(analysis.getMessage())
                    .facilities(List.of())
                    .build();
        }

        // 3. LocationService 반경 검색 (첫 번째 추천 카테고리 기준)
        String primaryCategory = categories.get(0);
        List<LocationService> nearby = locationServiceRepository
                .findNearbyByCategory(lat, lng, radius, primaryCategory);

        // 4. DTO 변환 (점수 계산은 Step 7에서 추가)
        List<PetRecommendFacilityDto> facilities = nearby.stream()
                .limit(10)
                .map(loc -> toDto(loc, lat, lng, analysis.getIntentTags()))
                .toList();

        return PetRecommendResponse.builder()
                .requestText(text)
                .analysis(analysis)
                .message(analysis.getMessage())
                .facilities(facilities)
                .build();
    }

    private PetRecommendResponse fallbackRecommend(
            String text, double lat, double lng, int radius) {
        log.warn("[PetRecommendationService] Python 서버 장애 — fallback 실행");
        // Step 7에서 키워드 기반 fallback 구현 예정
        return PetRecommendResponse.builder()
                .requestText(text)
                .analysis(null)
                .message("현재 반려생활 의도 분석 서버가 응답하지 않아 기본 검색 결과를 제공합니다.")
                .facilities(List.of())
                .build();
    }

    private PetRecommendFacilityDto toDto(
            LocationService loc, double userLat, double userLng, List<String> intentTags) {
        double distM = calcDistanceM(userLat, userLng, loc.getLat(), loc.getLng());
        return PetRecommendFacilityDto.builder()
                .id(loc.getId())
                .name(loc.getName())
                .address(loc.getAddress())
                .distanceM(Math.round(distM * 10.0) / 10.0)
                .rating(loc.getRating() != null ? loc.getRating() : 0.0)
                .reviewCount(loc.getReviewCount() != null ? loc.getReviewCount() : 0)
                .finalScore(0.0)  // Step 7에서 계산
                .matchReasons(List.of("nearby"))
                .build();
    }

    private double calcDistanceM(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng/2)*Math.sin(dLng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
```

### `backend/main/java/com/linkup/Petory/domain/petRecommendation/controller/PetRecommendationController.java`

```java
package com.linkup.Petory.domain.petRecommendation.controller;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendResponse;
import com.linkup.Petory.domain.petRecommendation.service.PetRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pet-recommend")
@RequiredArgsConstructor
public class PetRecommendationController {

    private final PetRecommendationService petRecommendationService;

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PetRecommendResponse> recommend(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam String text,
            @RequestParam(defaultValue = "3000") int radius,
            @RequestParam(required = false) String petType) {

        return ResponseEntity.ok(
                petRecommendationService.recommend(text, lat, lng, radius, petType));
    }
}
```

## LocationServiceRepository에 추가할 메서드

기존 `LocationServiceRepository`에 아래 메서드가 없으면 추가:

```java
@Query(value = """
    SELECT * FROM location_service
    WHERE (category1 = :category OR category2 = :category OR category3 = :category)
      AND ST_Distance_Sphere(point(lng, lat), point(:lng, :lat)) <= :radius
    ORDER BY ST_Distance_Sphere(point(lng, lat), point(:lng, :lat))
    LIMIT 20
    """, nativeQuery = true)
List<LocationService> findNearbyByCategory(
    @Param("lat") double lat,
    @Param("lng") double lng,
    @Param("radius") int radius,
    @Param("category") String category
);
```

> 기존에 유사한 메서드가 있으면 재사용하고 파라미터만 맞춤.

## Acceptance Criteria

```bash
# 1. 컴파일 확인
cd /Users/maknkkong/project/Petory
./gradlew compileJava
# 기대: BUILD SUCCESSFUL

# 2. Python + Spring 모두 실행 후
curl "http://localhost:8080/api/pet-recommend?lat=37.501&lng=127.039&text=우리%20강아지가%20귀를%20자꾸%20긁어요&radius=3000" \
  -H "Authorization: Bearer <token>"
# 기대: analysis.intentDomain=MEDICAL, facilities 배열 반환
```
