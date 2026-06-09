# Step 1 — CareRequest petType 전달 (Phase 0.1)

## 목표

이벤트 기반 signal 경로 중 케어 요청 경로에서 `petType`을 Python NLP 서버까지 전달한다.
커뮤니티/위치검색은 특정 pet 선택 UX가 없으므로 이번 단계에서 null 유지한다.

## 배경

- 현재 `PetIntentSignalEventListener.analyze()`는 항상 `petIntentClient.analyze(text, null)` 호출
- `CareRequestCreatedEvent`에는 petType 정보가 없다
- `CareRequestService`에서 이벤트를 발행할 때 `saved.getPet()`이 존재하면 petType을 알 수 있다
- Python `classify(text, pet_type)` 시그니처는 이미 열려 있다 (`pet_type=None` 이면 기존과 동일 동작)

## 변경 파일

### 1. `CareRequestCreatedEvent.java`

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/event/CareRequestCreatedEvent.java
```

`petType: String` (nullable) 필드 추가.

```java
@Getter
public class CareRequestCreatedEvent extends ApplicationEvent {

    private final Long userIdx;
    private final Long careRequestId;
    private final String text;
    private final String petType;  // "DOG" | "CAT" | "BIRD" | "RABBIT" | "HAMSTER" | "ETC" | null

    public CareRequestCreatedEvent(Object source, Long userIdx, Long careRequestId,
                                   String text, String petType) {
        super(source);
        this.userIdx = userIdx;
        this.careRequestId = careRequestId;
        this.text = text;
        this.petType = petType;
    }
}
```

기존 생성자 대신 새 생성자로 교체 (기존 getter 유지, `getPetType()` 추가).

---

### 2. `CareRequestService.java`

```
backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java
```

이벤트 발행 시 petType을 포함한다.

변경 전:
```java
eventPublisher.publishEvent(new CareRequestCreatedEvent(
        this, user.getIdx(), saved.getIdx(),
        saved.getTitle() + " " + saved.getDescription()));
```

변경 후:
```java
String petType = saved.getPet() != null ? saved.getPet().getPetType().name() : null;
eventPublisher.publishEvent(new CareRequestCreatedEvent(
        this, user.getIdx(), saved.getIdx(),
        saved.getTitle() + " " + saved.getDescription(),
        petType));
```

---

### 3. `PetIntentSignalEventListener.java`

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetIntentSignalEventListener.java
```

`handle(CareRequestCreatedEvent)`에서 petType을 `analyze()`로 전달.

`analyze()` 메서드에 `petType` 파라미터 추가:

```java
private void analyze(Long userIdx, String sourceType, Long sourceId,
                     String text, String petType) {
    try {
        Optional<PetIntentAnalyzeResponse> result = petIntentClient.analyze(text, petType);
        result.ifPresent(analysis ->
                signalService.saveIfConfident(userIdx, sourceType, sourceId, analysis));
    } catch (Exception e) {
        log.warn("[SignalListener] 분석 실패 — 원 액션에 영향 없음. sourceType={} error={}",
                sourceType, e.getMessage());
    }
}
```

기존 커뮤니티/위치검색 호출은 `analyze(..., null)`로 유지:

```java
// CommunityPostCreatedEvent
analyze(event.getUserIdx(), "COMMUNITY", event.getPostId(), event.getText(), null);

// CareRequestCreatedEvent
analyze(event.getUserIdx(), "CARE", event.getCareRequestId(), event.getText(), event.getPetType());

// LocationSearchPerformedEvent
analyze(event.getUserIdx(), "LOCATION_SEARCH", null, keyword, null);
```

## 검증

```bash
./gradlew compileJava
```

컴파일 오류 없으면 완료.
`CareRequestCreatedEvent` 생성자 파라미터가 늘었으므로, 다른 곳에서 동일 이벤트를 생성하는 코드가 있으면 함께 수정 필요 — `grep -r "CareRequestCreatedEvent"` 로 발행 지점 확인.
