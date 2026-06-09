# Step 4 — domain×urgency TTL (Phase 0.4)

## 목표

단일 `SIGNAL_TTL_DAYS = 7` 상수를 `ttlDaysFor(domain, urgency)` 메서드로 교체한다.
MEDICAL+HIGH는 1일, MEDICAL+NORMAL은 3일, LODGING_TRAVEL은 14일로 domain×urgency별 TTL 적용.

## 배경

- MEDICAL+HIGH 위급 신호를 7일씩 들고 있으면 증상 해결 후에도 stale 카드가 노출됨
- LODGING_TRAVEL(여행 계획)은 7일보다 긴 14일이 자연스러움
- Step 2에서 urgency가 저장되어 있어야 이 단계가 의미 있음 (Step 2 완료 전제)

## TTL 기준

| 조건 | TTL | 이유 |
|------|-----|------|
| MEDICAL + HIGH | 1일 | 위급 signal stale 방지 |
| MEDICAL (NORMAL/LOW/null) | 3일 | 증상 signal stale 방지 |
| LODGING_TRAVEL | 14일 | 여행 계획은 비교적 장기 |
| 그 외 | 7일 | 기존 기본값 유지 |

## 변경 파일

### `UserPetIntentSignalService.java`

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java
```

#### 4-A. 상수 정리

기존 `SIGNAL_TTL_DAYS = 7` 상수는 `ttlDaysFor()` 내부 default로 흡수 — 외부 상수 제거.

#### 4-B. `ttlDaysFor()` 메서드 추가

```java
private int ttlDaysFor(String domain, String urgency) {
    if ("MEDICAL".equals(domain)) {
        return "HIGH".equals(urgency) ? 1 : 3;
    }
    if ("LODGING_TRAVEL".equals(domain)) {
        return 14;
    }
    return 7;
}
```

#### 4-C. `saveIfConfident()` TTL 적용 교체

기존:
```java
.expiresAt(LocalDateTime.now().plusDays(SIGNAL_TTL_DAYS))
```

교체:
```java
int ttlDays = ttlDaysFor(analysis.getIntentDomain(), analysis.getUrgency());
// ... builder ...
.expiresAt(LocalDateTime.now().plusDays(ttlDays))
```

로그도 함께 수정 (`expiresInDays={}` 값을 `SIGNAL_TTL_DAYS` → `ttlDays` 변수로):
```java
log.info("[Signal] 저장 완료 userIdx={} sourceType={} sourceId={} domain={} urgency={} confidence={} ttlDays={}",
        userIdx, sourceType, sourceId,
        analysis.getIntentDomain(), analysis.getUrgency(),
        analysis.getConfidence(), ttlDays);
```

## 검증

```bash
./gradlew compileJava
```

동작 확인 포인트:
- `ttlDaysFor("MEDICAL", "HIGH")` → 1
- `ttlDaysFor("MEDICAL", "NORMAL")` → 3
- `ttlDaysFor("MEDICAL", null)` → 3
- `ttlDaysFor("LODGING_TRAVEL", "LOW")` → 14
- `ttlDaysFor("FOOD_SNACK", "LOW")` → 7
- `ttlDaysFor(null, null)` → 7
