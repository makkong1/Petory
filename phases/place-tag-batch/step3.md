# Step 3: Spring tag_match_score 활성화 — 엔티티 tags 필드 + ScoreCalculator 업데이트

## 목표
`LocationService` 엔티티에 `tags` 필드를 추가하고,
`PetRecommendScoreCalculator`에서 `tag_match_score`를 실제로 계산하도록 업데이트한다.

## 배경
- 현재 `PetRecommendScoreCalculator`에서 `tagScore = 0.0` (Phase 4 전까지 0으로 고정)
- Step 2에서 `locationservice.tags JSON` 컬럼이 생성됨
- `intentTags` (Python 분석 결과) vs `locationTags` (DB) 교집합 비율로 점수 계산
- `LocationService` 엔티티 경로: `domain/location/entity/LocationService.java`
- `PetRecommendScoreCalculator` 경로: `domain/petRecommendation/scoring/PetRecommendScoreCalculator.java`

## 변경할 파일

### 1. `LocationService.java`에 tags 필드 추가

기존 파일에서 `score` 필드 근처에 추가:

```java
@Column(name = "tags", columnDefinition = "JSON")
@Convert(converter = JsonListConverter.class)
private List<String> tags;
```

> `JsonListConverter`가 없으면 아래 방법으로 대체:
```java
@Column(name = "tags", columnDefinition = "JSON")
private String tagsJson;

@Transient
public List<String> getTagList() {
    if (tagsJson == null || tagsJson.isBlank()) return List.of();
    try {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(tagsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    } catch (Exception e) { return List.of(); }
}
```

기존 프로젝트에 `@Convert` 컨버터 패턴이 있으면 그것을 따른다.

### 2. `PetRecommendScoreCalculator.java` 수정

`calcScore()` 메서드에서 `tagScore = 0.0` 부분을 실제 계산으로 교체:

```java
// 기존
double tagScore = 0.0;  // Phase 4 전까지 0

// 변경 후
double tagScore = calcTagMatchScore(dto.getLocationTags(), intentTags);
```

`calcTagMatchScore` 메서드 추가:

```java
private double calcTagMatchScore(List<String> locationTags, List<String> intentTags) {
    if (locationTags == null || locationTags.isEmpty()
            || intentTags == null || intentTags.isEmpty()) return 0.0;
    long matched = intentTags.stream()
            .filter(locationTags::contains)
            .count();
    return (double) matched / intentTags.size();
}
```

### 3. `PetRecommendFacilityDto.java`에 locationTags 필드 추가

```java
private List<String> locationTags;
```

### 4. `PetRecommendationService.java`의 `toDto()` 수정

`LocationService`에서 tags 읽어서 DTO에 포함:

```java
private PetRecommendFacilityDto toDto(
        LocationService loc, double userLat, double userLng, List<String> intentTags) {
    double distM = ...;
    return PetRecommendFacilityDto.builder()
            ...
            .locationTags(loc.getTagList())  // tags 추가
            .build();
}
```

## Acceptance Criteria

```bash
# 1. 컴파일
cd /Users/maknkkong/project/Petory
./gradlew compileJava
# 기대: BUILD SUCCESSFUL

# 2. API 호출 후 finalScore가 0이 아닌 값 + tag_match 포함 확인
curl -s -G "http://localhost:8080/api/pet-recommend" \
  --data-urlencode "text=우리 강아지 귀를 자꾸 긁어요" \
  --data-urlencode "lat=37.501" \
  --data-urlencode "lng=127.039" \
  --data-urlencode "radius=5000" \
  -H "Authorization: Bearer <token>" | python -m json.tool | grep -A3 "matchReasons"
# 기대: matchReasons에 "tag_match:ear" 또는 "tag_match:medical" 포함
```
