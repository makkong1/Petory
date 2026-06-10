# petType enum 불일치로 인한 signal 무음 드롭 (2026-06-10)

**도메인**: petRecommendation (의도 분석 기반 추천)  
**발견 경위**: ai-intent-analysis-transition 문서 작성 중 코드 비교 분석  
**상태**: ✅ 수정 완료 (2026-06-10)

---

## 증상

`BIRD`, `RABBIT`, `HAMSTER`, `ETC` 종의 케어 요청을 작성해도 `user_pet_intent_signal`이 저장되지 않는다.

사용자 피드백 없음. 로그에 warn만 찍히고 정상 흐름처럼 보인다.

---

## 원인 분석

### Java 이벤트 (`CareRequestCreatedEvent.java:16`)

```java
// "DOG" | "CAT" | "BIRD" | "RABBIT" | "HAMSTER" | "ETC" | null
private final String petType;
```

### Python request schema (`petory-nlp-server/app/schemas/request.py:5`)

```python
class PetType(str, Enum):
    DOG = "DOG"
    CAT = "CAT"
    OTHER = "OTHER"
```

Java에서 `BIRD`, `RABBIT`, `HAMSTER`, `ETC`를 그대로 Python에 넘기면 Pydantic이 422 Unprocessable Entity를 반환한다.

### 드롭 경로 (`PetIntentClient.java:113`)

```
CareRequestCreatedEvent (petType=BIRD)
  → PetIntentClient.analyze()
  → POST /api/pet-intent/analyze  →  Python 422 응답
  → RestClientException catch
  → Optional.empty() 반환
  → signalService.saveIfConfident() 미호출
  → signal 저장 안 됨 (warn 로그만)
```

`PetIntentSignalEventListener`의 `analyze()` 내부에서 `RestClientException`을 잡아 warn만 찍고 종료하기 때문에 원 트랜잭션(케어 요청 생성)에는 영향 없이 signal만 조용히 드롭된다.

---

## 해결 방법

수정 위치: `PetIntentClient.java` — `analyze()` 내부에서 request를 빌드하기 전 petType을 정규화한다.

```java
// Before
PetIntentAnalyzeRequest req = PetIntentAnalyzeRequest.builder()
        .text(text)
        .petType(petType)
        .build();

// After
PetIntentAnalyzeRequest req = PetIntentAnalyzeRequest.builder()
        .text(text)
        .petType(normalizePetType(petType))
        .build();
```

```java
private static String normalizePetType(String petType) {
    if (petType == null) return null;
    return switch (petType) {
        case "DOG", "CAT" -> petType;
        default -> "OTHER";
    };
}
```

Python `PetType` enum(`DOG|CAT|OTHER`)을 기준으로 Java → Python 경계에서 한 번만 정규화한다. Python 스키마와 Java enum 양쪽 중 **Java에서 변환**하는 게 변경 범위가 최소다.

---

## 재발 방지

- Java↔Python API 계약 변경 시 `PetIntentClient` 정규화 메서드도 함께 수정한다.
- Python `PetType` enum 값이 바뀌면 `normalizePetType()`의 허용 목록을 업데이트한다.
- 향후 `petType`이 AI 분류 로직에 실제로 사용될 경우 Python enum을 Java와 맞추는 방향으로 재검토한다 (`ai-intent-analysis-transition-2026-06-10.md` §5 참고).
