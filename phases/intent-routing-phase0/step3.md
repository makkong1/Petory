# Step 3 — domain×urgency confidence threshold Map (Phase 0.3)

## 목표

단일 `CONFIDENCE_THRESHOLD = 0.6` 상수를 domain별 Map으로 교체하고,
`thresholdFor(domain, urgency)` 메서드로 urgency 조합까지 처리한다.

## 배경

- 현재 MEDICAL 오탐과 FOOD_SNACK 누락이 같은 0.6 기준으로 처리됨
- MEDICAL+HIGH는 위급 신호 누락 비용이 크므로 threshold를 낮춰야(완화) 한다
- FOOD_SNACK/WALK_OUTING/CAFE_DINING은 Python 1차 필터(0.45)와 맞추면 충분 — Spring 2차가 같은 값이면 domain 오탐 비용이 낮아 허용 가능
- Step 2에서 urgency가 저장되어 있어야 이 단계가 의미 있음 (Step 2 완료 전제)

## 변경 파일

### `UserPetIntentSignalService.java`

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java
```

#### 3-A. 상수 교체

기존:
```java
private static final double CONFIDENCE_THRESHOLD = 0.6;
```

교체:
```java
private static final Map<String, Double> DOMAIN_THRESHOLDS = Map.of(
    "MEDICAL",          0.65,  // urgency=HIGH 시 thresholdFor()에서 0.55로 완화
    "FOOD_SNACK",       0.45,  // Python 1차 필터와 동일 — 오탐 비용 낮음
    "SUPPLIES",         0.45,
    "WALK_OUTING",      0.45,
    "CAFE_DINING",      0.45
);
private static final double DEFAULT_THRESHOLD = 0.60;
```

#### 3-B. `thresholdFor()` 메서드 추가

```java
private double thresholdFor(String domain, String urgency) {
    // MEDICAL+HIGH: 위급 signal 누락 비용 > 오탐 비용 → threshold 완화
    if ("MEDICAL".equals(domain) && "HIGH".equals(urgency)) {
        return 0.55;
    }
    return DOMAIN_THRESHOLDS.getOrDefault(domain, DEFAULT_THRESHOLD);
}
```

#### 3-C. `saveIfConfident()` threshold 판단 교체

기존:
```java
if (analysis == null || analysis.getConfidence() < CONFIDENCE_THRESHOLD) {
```

교체:
```java
if (analysis == null
        || analysis.getConfidence() < thresholdFor(analysis.getIntentDomain(), analysis.getUrgency())) {
```

로그도 함께 수정:
```java
log.debug("[Signal] 저장 스킵 — confidence 미달 또는 분석 없음. userIdx={} domain={} urgency={} confidence={} threshold={}",
        userIdx, sourceType,
        analysis != null ? analysis.getIntentDomain() : "null",
        analysis != null ? analysis.getUrgency() : "null",
        analysis != null ? analysis.getConfidence() : "null",
        analysis != null ? thresholdFor(analysis.getIntentDomain(), analysis.getUrgency()) : "null");
```

## 검증

```bash
./gradlew compileJava
```

동작 확인 포인트:
- MEDICAL + confidence 0.60 → 저장 스킵 (threshold 0.65)
- MEDICAL + HIGH + confidence 0.60 → 저장 통과 (threshold 0.55)
- FOOD_SNACK + confidence 0.46 → 저장 통과 (threshold 0.45)
- GROOMING + confidence 0.58 → 저장 스킵 (DEFAULT 0.60)
