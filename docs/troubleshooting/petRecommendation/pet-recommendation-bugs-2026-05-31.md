# petRecommendation 도메인 — 버그 & 보안 이슈 (2026-05-31)

**도메인**: petRecommendation (의도 분석 기반 추천)  
**점검 기준**: 코드 리뷰 2차 검증  
**상태 요약**:

| ID | 위치 | 증상 | 심각도 | 상태 |
|----|------|------|--------|------|
| T1 | `PetIntentSignalEventListener` | 트랜잭션 롤백 시 dangling signal 발생 가능 | 🔴 High | ✅ 수정 완료 (2026-05-31) |
| T2 | `PetRecommendationController` | 데이터 변경 API에 인가 어노테이션 없음 | 🔴 High | ✅ 수정 완료 (refactor R1에서 처리) |
| T3 | `PetRecommendationController` | `text` 파라미터 길이 무제한 | 🟡 Medium | ✅ 수정 완료 (2026-05-31) |
| T4 | `pet_intent_router.py` | NLP 분석 중 예외 발생 시 구조화된 에러 응답 없음 | 🟡 Medium | ✅ 수정 완료 (2026-05-31) |
| T5 | `PetRecommendationController` | `interactionType` 임의 문자열 그대로 저장 | 🟡 Medium | ✅ 수정 완료 (2026-05-31) |

---

## T1 — `@EventListener` + `@Async`: 트랜잭션 커밋 전 실행

### 발생 위치

`PetIntentSignalEventListener.java:24, 30`

### 문제

`BoardService.createBoard()`와 `CareRequestService.createCareRequest()`는 `@Transactional` 메서드 안에서 `publishEvent()`를 호출한다. `@EventListener`는 `publishEvent()` 호출 시점(트랜잭션이 아직 열려 있는 상태)에 이벤트를 전달하며, `@Async`가 새 스레드를 띄운다.

새 스레드의 `saveIfConfident()`가 signal을 저장하는 시점에, 원본 트랜잭션(게시글/케어 요청)이 **아직 커밋되지 않았거나 롤백**될 수 있다.

### 구체적 시나리오

```
[Thread A: BoardService.createBoard()]        [Thread B: @Async EventListener]
  @Transactional BEGIN
    Board saved = boardRepository.save(...)
    eventPublisher.publishEvent(event)    →  스케줄됨
      ...                                    [Thread B 시작]
    attachmentFileService.sync(...)            petIntentClient.analyze(text)
    // 예외 발생 → rollback 예정              signal.sourceId = saved.getIdx()
  @Transactional ROLLBACK                     signalRepository.save(signal)  ← 이미 저장됨
```

Board 행은 롤백되어 존재하지 않지만, signal의 `source_id`는 해당 Board ID를 가리키는 행이 DB에 남는다. FK 제약이 없으므로 DB 오류는 발생하지 않지만 무결성 기대 위반.

### 이벤트별 트랜잭션 컨텍스트 차이

| 이벤트 | 발행 위치 | 트랜잭션 유무 | 적용 어노테이션 |
|--------|----------|------------|---------------|
| `CommunityPostCreatedEvent` | `BoardService.createBoard()` (@Transactional) | ✅ 있음 | `@TransactionalEventListener(AFTER_COMMIT)` |
| `CareRequestCreatedEvent` | `CareRequestService.createCareRequest()` (@Transactional) | ✅있음 | `@TransactionalEventListener(AFTER_COMMIT)` |
| `LocationSearchPerformedEvent` | `LocationServiceService.publishSearchEvent()` (private, 트랜잭션 없음) | ❌ 없음 | `@EventListener` 유지 |

`LocationSearchPerformedEvent`에 `@TransactionalEventListener`를 적용하면 트랜잭션이 없으므로 리스너 자체가 **실행되지 않는다**. 이벤트별로 어노테이션을 분리해야 한다.

### 수정 방안

```java
// PetIntentSignalEventListener.java

// CommunityPost — 트랜잭션 커밋 후 실행
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void handle(CommunityPostCreatedEvent event) {
    analyze(event.getUserIdx(), "COMMUNITY", event.getPostId(), event.getText());
}

// CareRequest — 트랜잭션 커밋 후 실행
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void handle(CareRequestCreatedEvent event) {
    analyze(event.getUserIdx(), "CARE", event.getCareRequestId(), event.getText());
}

// LocationSearch — 트랜잭션 없으므로 @EventListener 유지
@EventListener
@Async
public void handle(LocationSearchPerformedEvent event) {
    if (event.getUserIdx() == null) return;
    analyze(event.getUserIdx(), "LOCATION_SEARCH", null, event.getKeyword());
}
```

### 주의 사항

`@TransactionalEventListener`는 기본적으로 **실행 중인 트랜잭션이 없으면 이벤트를 무시**한다. `AFTER_COMMIT` phase는 커밋 완료 후 동기 실행이 기본이지만, `@Async`와 함께 사용하면 커밋 완료 후 비동기 스레드가 시작된다. 순서: `커밋 완료 → @Async 스케줄 → 새 스레드에서 실행`.

---

## T2 — 데이터 변경 API에 인가 어노테이션 없음

### 발생 위치

`PetRecommendationController.java:48-57`

### 문제

`POST /api/pet-recommend/interact`와 `GET /api/pet-recommend/signals`에 `@PreAuthorize`가 없다. SecurityConfig의 `/api/**` catch-all이 **인증(Authentication)**은 강제하지만, 명시적 **인가(Authorization)** 선언이 없어 코드 의도가 불명확하다.

```java
// 현재
@PostMapping("/interact")
public ResponseEntity<Void> interact(
        @AuthenticationPrincipal UserDetails userDetails,
        ...) { ... }
```

또한 `/interact`에서 `userDetails`가 null이면 `NullPointerException`이 발생한다. SecurityConfig가 인증을 강제한다고 가정하면 null이 아니어야 하지만, 코드 자체에는 null 검사가 없어 방어 코드가 부재하다.

### 수정 방안

```java
@PostMapping("/interact")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Void> interact(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam("locationIdx") Long locationIdx,
        @RequestParam("type") String interactionType) {
    if (userDetails == null) return ResponseEntity.status(401).build();
    ...
}

@GetMapping("/signals")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals(
        @AuthenticationPrincipal UserDetails userDetails) {
    if (userDetails == null) return ResponseEntity.ok(List.of());
    ...
}
```

---

## T3 — `text` 파라미터 길이 무제한

### 발생 위치

`PetRecommendationController.java:30`

### 문제

`GET /api/pet-recommend?text=...`는 임의 길이의 텍스트를 받아 Python NLP 서버에 그대로 전달한다. `sentence-transformers`의 max sequence length(기본 512 tokens)를 초과하면 모델이 조용히 잘라낸다. 대용량 텍스트는 NLP 서버의 메모리·CPU를 과도하게 사용할 수 있고, URL 자체도 브라우저/프록시의 최대 URL 길이를 초과할 수 있다.

### 수정 방안

```java
// 컨트롤러 파라미터에 검증 추가
@GetMapping
public ResponseEntity<PetRecommendResponse> recommend(
        @RequestParam("lat") double lat,
        @RequestParam("lng") double lng,
        @RequestParam("text") @Size(max = 500, message = "text는 500자 이하입니다") String text,
        ...) { ... }

// 클래스에 @Validated 추가
@RestController
@Validated
@RequestMapping("/api/pet-recommend")
public class PetRecommendationController { ... }
```

또는 서비스 레이어에서 방어적으로 잘라내기:
```java
String safeText = (text != null && text.length() > 500) ? text.substring(0, 500) : text;
```

---

## T4 — Python router 예외 미처리

### 발생 위치

`petory-nlp-server/app/api/pet_intent_router.py:16`

### 문제

`classify()`, `extract_tags()`, `extract_keywords()` 중 어느 곳에서 예외가 발생해도 FastAPI 기본 500 응답이 반환된다. Spring의 `PetIntentClient`는 `RestClientException`을 잡지만, 응답 본문 파싱 실패(`@JsonIgnoreProperties(ignoreUnknown = true)` 덕에 대부분 통과)나 예상치 못한 형태의 500은 `Optional.empty()`가 아닌 예외로 떨어질 수 있다.

```python
# 현재
@router.post("/analyze", response_model=PetIntentAnalyzeResponse)
def analyze(req: PetIntentAnalyzeRequest):
    intent, domain, confidence = classify(req.text)  # 예외 시 500
    ...
```

### 수정 방안

```python
from fastapi import HTTPException
import logging

logger = logging.getLogger(__name__)

@router.post("/analyze", response_model=PetIntentAnalyzeResponse)
def analyze(req: PetIntentAnalyzeRequest):
    try:
        intent, domain, confidence = classify(req.text)
        keywords = extract_keywords(req.text)
        intent_tags = extract_tags(req.text, domain)
        urgency = judge_urgency(req.text, domain)
    except Exception as e:
        logger.error("NLP 분석 실패: %s", str(e), exc_info=True)
        raise HTTPException(status_code=500, detail="의도 분석 중 오류가 발생했습니다.")
    
    # 이후 로직 유지
    ...
```

Spring의 `PetIntentClient`는 `RestClientException`으로 5xx를 이미 잡으므로 이 수정 후 Spring side는 변경 불필요.

---

## T5 — `interactionType` 임의 문자열 저장

### 발생 위치

`PetRecommendationController.java:51`, `PlaceInteractionLog.java:25`

### 문제

`POST /api/pet-recommend/interact?type=...`에서 type 파라미터를 검증 없이 DB에 저장한다. "VIEW", "NAVIGATE", "FAVORITE" 외 임의 값(예: `type=DROP_TABLE`)도 저장된다.

```java
// 현재
@RequestParam("type") String interactionType
// → PlaceInteractionLog.interactionType에 그대로 저장
```

### 수정 방안

```java
// 허용 값을 enum으로 제한
public enum InteractionType {
    VIEW, NAVIGATE, FAVORITE
}

// Controller 파라미터
@RequestParam("type") InteractionType interactionType

// PlaceInteractionLog 저장 시
.interactionType(interactionType.name())
```

또는 `@Pattern` 어노테이션으로 간단히 제한:
```java
@RequestParam("type")
@Pattern(regexp = "VIEW|NAVIGATE|FAVORITE", message = "유효하지 않은 interactionType입니다")
String interactionType
```

---

## 주의 사항 (수정 전 확인)

### T1 수정 시

`@TransactionalEventListener`는 기본적으로 `AFTER_COMMIT` 후 동기로 실행된다. `@Async`와 조합하면 커밋 완료 직후 별도 스레드에서 실행되므로 트랜잭션 컨텍스트는 없다. `saveIfConfident()`의 `@Transactional`이 새 트랜잭션을 열므로 정상 동작한다.

단, `@TransactionalEventListener`는 **트랜잭션 없이 발행된 이벤트는 무시**한다. `LocationSearchPerformedEvent`는 반드시 `@EventListener`로 유지해야 한다.

### T2 수정 시

`/api/pet-recommend` (GET)는 현재 공개 접근을 의도했을 수 있다(petType 선택적, userDetails 없이 분석 가능). 팀 정책에 따라 인증 없이 허용할 수도 있다. 단, `/interact` POST는 반드시 인증 필요.

---

*작성 기준: `domain/petRecommendation/**`, `petory-nlp-server/app/**` 코드 리뷰 2026-05-31*
