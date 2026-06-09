# Step 2 — urgency 저장 + buildCardMessage 확장 (Phase 0.2)

## 목표

Python NLP 응답의 `urgency`(HIGH/NORMAL/LOW)를 DB에 저장하고, 카드 메시지 생성에 반영한다.
특히 `MEDICAL + HIGH` 상황에서 기존 일반 문구 대신 위급 안내 문구를 내려준다.

## 배경

- `PetIntentAnalyzeResponse.urgency` 필드는 이미 존재 (Spring DTO)
- `UserPetIntentSignal` 엔티티에 urgency 컬럼이 없어 버려지고 있음
- `buildCardMessage(domain, categories)` → `buildCardMessage(domain, urgency, categories)` 시그니처 확장
- Phase 1에서 constraints 반영 시 `buildCardMessage(domain, urgency, constraints, categories)`로 추가 확장 예정

## 변경 파일

### 1. SQL migration 신규 생성

```
backend/main/resources/sql/migration/user-pet-intent-signal-add-urgency-column.sql
```

```sql
ALTER TABLE user_pet_intent_signal
    ADD COLUMN urgency VARCHAR(10) NULL COMMENT 'HIGH | NORMAL | LOW (NLP urgency_rules 결과)'
    AFTER confidence;
```

---

### 2. `UserPetIntentSignal.java`

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/entity/UserPetIntentSignal.java
```

`confidence` 필드 다음에 `urgency` 추가:

```java
@Column(name = "confidence", nullable = false)
private Double confidence;

@Column(name = "urgency", length = 10)
private String urgency;  // "HIGH" | "NORMAL" | "LOW" | null (레거시 행 호환)
```

Builder/AllArgsConstructor는 Lombok이 자동 처리 — 기존 `builder()` 호출에 `.urgency(...)` 추가 필요.

---

### 3. `UserPetIntentSignalResponse.java`

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/dto/UserPetIntentSignalResponse.java
```

`urgency` 필드 추가:

```java
private String urgency;  // "HIGH" | "NORMAL" | "LOW" | null
```

---

### 4. `UserPetIntentSignalService.java`

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java
```

#### 4-A. `saveIfConfident()` — urgency 저장

builder에 `.urgency(analysis.getUrgency())` 추가:

```java
UserPetIntentSignal signal = UserPetIntentSignal.builder()
        .userIdx(userIdx)
        .sourceType(sourceType)
        .sourceId(sourceId)
        .intentDomain(analysis.getIntentDomain())
        .intent(analysis.getIntent())
        .recommendedCategories(categoriesJson)
        .confidence(analysis.getConfidence())
        .urgency(analysis.getUrgency())   // 추가
        .intentTags(tagsJson)
        .expiresAt(LocalDateTime.now().plusDays(SIGNAL_TTL_DAYS))
        .build();
```

#### 4-B. `toResponse()` — urgency 포함, buildCardMessage 시그니처 변경

```java
private UserPetIntentSignalResponse toResponse(UserPetIntentSignal s) {
    List<String> categories = parseJson(s.getRecommendedCategories());
    List<String> tags = parseJson(s.getIntentTags());
    String cardMessage = buildCardMessage(s.getIntentDomain(), s.getUrgency(), categories);
    String targetCategory = categories.isEmpty() ? null : categories.get(0);
    return UserPetIntentSignalResponse.builder()
            .intentDomain(s.getIntentDomain())
            .intent(s.getIntent())
            .recommendedCategories(categories)
            .confidence(s.getConfidence())
            .urgency(s.getUrgency())                                          // 추가
            .intentTags(tags)
            .cardMessage(cardMessage)
            .actionLabel(targetCategory != null ? "근처 " + targetCategory + " 보기" : "주변 서비스 보기")
            .targetTab("location")
            .targetCategory(targetCategory)
            .build();
}
```

#### 4-C. `buildCardMessage()` — urgency 파라미터 추가 + MEDICAL HIGH 분기

```java
private String buildCardMessage(String domain, String urgency,
        @SuppressWarnings("unused") List<String> categories) {
    boolean isHigh = "HIGH".equals(urgency);
    return switch (domain != null ? domain : "") {
        case "MEDICAL" -> isHigh
                ? "위급할 수 있어요. 가까운 동물병원에 바로 문의하세요."
                : "최근 건강 관련 고민이 있어 보여요.";
        case "GROOMING"        -> "반려동물 미용이 필요해 보여요.";
        case "CAFE_DINING"     -> "반려동물과 나들이 어떠세요?";
        case "LODGING_TRAVEL"  -> "여행 계획 중이신가요?";
        case "SUPPLIES"        -> "반려동물 용품이 필요해 보여요.";
        case "FOOD_SNACK"      -> "반려동물 먹거리가 필요해 보여요.";
        case "WALK_OUTING"     -> "반려동물과 산책하기 좋은 곳을 찾아드릴게요.";
        case "DAYCARE_BOARDING"-> "반려동물 돌봄 서비스가 필요해 보여요.";
        case "CULTURE_SPACE"   -> "반려동물과 함께하는 문화 공간을 찾아보세요.";
        default                -> "최근 입력을 바탕으로 추천합니다.";
    };
}
```

## 검증

```bash
./gradlew compileJava
```

SQL은 로컬 MySQL에 직접 실행:
```sql
ALTER TABLE user_pet_intent_signal
    ADD COLUMN urgency VARCHAR(10) NULL COMMENT 'HIGH | NORMAL | LOW'
    AFTER confidence;
```
