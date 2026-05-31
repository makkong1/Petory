# petRecommendation 도메인 — 리팩토링 백로그 (2026-05-31)

**도메인**: petRecommendation (의도 분석 기반 추천)  
**점검 기준**: 코드 리뷰 2차 검증  
**범위**: 동작은 하지만 유지보수·확장·일관성·운영 측면에서 개선이 필요한 항목

| ID | 위치 | 분류 | 설명 | 우선순위 | 상태 |
|----|------|------|------|--------|------|
| R1 | `PetRecommendationController` | 레이어 설계 | `UsersRepository` 컨트롤러 직접 주입 | 🔴 높음 | ✅ 완료 |
| R2 | `UserPetIntentSignalRepository` | 성능 | `findActiveByUser` LIMIT 없음 | 🟡 중간 | ✅ 완료 |
| R3 | `UserPetIntentSignalService` | 정합성 | 같은 도메인 signal 무한 누적 | 🟡 중간 | ✅ 완료 |
| R4 | `PlaceInteractionLogRepository` | 코드품질 | `Object[]` 프로젝션 타입 안전성 없음 | 🟢 낮음 | ✅ 완료 |
| R5 | `PetIntentAnalyzeResponse` | 코드품질 | `@Data` → `@Getter` (불필요한 Setter 노출) | 🟢 낮음 | ✅ 완료 |
| R6 | `tag_extractor.py` | 코드품질 | `intent_tags.yml` 로드하지만 사실상 미사용 | 🟢 낮음 | 🔵 미적용 (배포 시 검토) |
| R7 | `intent_classifier.py` | 문서화 | `petType` 파라미터 무시 — 의도적 결정 미표기 | 🟢 낮음 | ✅ 완료 |
| R8 | `main.py` | 운영 | 임베딩 모델 워밍업 없음 | 🟢 낮음 | ✅ 완료 |
| R9 | `config.py` / `UserPetIntentSignalService` | 문서화 | Python 0.45 vs Spring 0.60 이중 threshold 미표기 | 🟢 낮음 | ✅ 완료 |

---

## R1 — Controller에서 `UsersRepository` 직접 주입

### 현재 코드

```java
// PetRecommendationController.java
@RequiredArgsConstructor
public class PetRecommendationController {
    private final UsersRepository usersRepository; // ← Repository 직접 주입

    @GetMapping("/signals")
    public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userIdx = usersRepository.findActiveByIdString(userDetails.getUsername())
                .orElseThrow().getIdx(); // ← 컨트롤러에서 사용자 조회
        return ResponseEntity.ok(signalService.getActiveSignals(userIdx));
    }
}
```

### 문제

- Controller → Repository 직접 참조로 4-Layer 구조(Controller → Service → Repository) 위반
- 사용자 조회 로직이 Controller와 Service에 중복 분산
- `orElseThrow()` 빈 Optional에서 `NoSuchElementException`이 발생하며 GlobalExceptionHandler 에서 적절히 잡히지 않을 수 있음

### 개선 방향

```java
// UserPetIntentSignalService에서 loginId 기반 조회로 통합
@Transactional(readOnly = true)
public List<UserPetIntentSignalResponse> getActiveSignals(String loginId) {
    Long userIdx = usersRepository.findActiveByIdString(loginId)
            .orElseThrow(() -> new UserNotFoundException()).getIdx();
    ...
}

// PetRecommendationController — Repository 의존성 제거
@GetMapping("/signals")
public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals(
        @AuthenticationPrincipal UserDetails userDetails) {
    if (userDetails == null) return ResponseEntity.ok(List.of());
    return ResponseEntity.ok(signalService.getActiveSignals(userDetails.getUsername()));
}

// PlaceInteractionService도 동일하게 loginId 받도록
```

### 수정 파일

- `PetRecommendationController.java` — `UsersRepository` 제거, `AuthenticatedUserIdResolver` 주입, `@PreAuthorize("isAuthenticated()")` 추가, `/signals`와 `/interact` 둘 다 수정
- `UserPetIntentSignalService.java` — `getActiveSignals(long userIdx)` (primitive)
- `PlaceInteractionService.java` — `record(long userIdx, ...)` (primitive)

### 상태: ✅ 완료 (2026-05-31)

---

## R2 — `findActiveByUser` LIMIT 없음

### 현재 코드

```java
// UserPetIntentSignalRepository.java
@Query("""
    SELECT s FROM UserPetIntentSignal s
    WHERE s.userIdx = :userIdx
      AND s.expiresAt > :now
    ORDER BY s.createdAt DESC
    """)
List<UserPetIntentSignal> findActiveByUser(
        @Param("userIdx") Long userIdx,
        @Param("now") LocalDateTime now);
```

### 문제

만료되지 않은 signal 전체를 반환한다. 활발한 사용자는 7일 TTL 동안 수십~수백 개의 signal이 쌓일 수 있다. 프론트는 추천 카드를 소수만 표시하므로 불필요한 데이터 로드.

### 개선 방향

Spring Boot 3.x(Hibernate 6)에서 JPQL LIMIT 지원:

```java
@Query("""
    SELECT s FROM UserPetIntentSignal s
    WHERE s.userIdx = :userIdx
      AND s.expiresAt > :now
    ORDER BY s.createdAt DESC
    LIMIT 10
    """)
List<UserPetIntentSignal> findActiveByUser(
        @Param("userIdx") Long userIdx,
        @Param("now") LocalDateTime now);
```

또는 Spring Data naming convention:
```java
List<UserPetIntentSignal> findTop10ByUserIdxAndExpiresAtAfterOrderByCreatedAtDesc(
        Long userIdx, LocalDateTime now);
```

### 적용 내역

`Pageable` 방식 채택. `findActiveByUser`에 `Pageable pageable` 파라미터 추가, `UserPetIntentSignalService`에서 `PageRequest.of(0, 10)` 전달. `ACTIVE_SIGNAL_LIMIT = 10` 상수로 관리.

### 상태: ✅ 완료 (2026-05-31)

---

## R3 — 같은 도메인 Signal 무한 누적

### 현재 동작

사용자가 `강아지가 귀를 긁어요` 내용의 게시글을 3일간 5개 작성하면 `MEDICAL` 도메인 signal 5개가 누적된다. 추천 카드에 동일 카드 5개가 중복 노출될 수 있다.

### 개선 방향

**Option A**: 저장 전 같은 `(userIdx, intentDomain)` 유효 signal 중복 확인

```java
// UserPetIntentSignalRepository
boolean existsByUserIdxAndIntentDomainAndExpiresAtAfter(
        Long userIdx, String intentDomain, LocalDateTime now);

// UserPetIntentSignalService.saveIfConfident()
if (signalRepository.existsByUserIdxAndIntentDomainAndExpiresAtAfter(
        userIdx, analysis.getIntentDomain(), LocalDateTime.now())) {
    log.debug("[Signal] 같은 도메인 유효 signal 존재 — 저장 스킵. domain={}", analysis.getIntentDomain());
    return;
}
```

**Option B**: Upsert — 같은 `(userIdx, intentDomain)` 행을 갱신

```sql
-- DB 레벨 UNIQUE 제약 추가
ALTER TABLE user_pet_intent_signal
  ADD UNIQUE KEY uq_user_domain (user_idx, intent_domain);
```

현재는 동일 도메인이 있어도 항상 INSERT이므로 추천 카드 중복이 발생한다. Option A가 코드 변경이 최소화되어 우선 적용하기 좋다.

### 적용 내역

Option A 채택. `UserPetIntentSignalRepository`에 `existsByUserIdxAndIntentDomainAndExpiresAtAfter` 추가. `saveIfConfident` 진입부에서 중복 확인 후 스킵.

### 상태: ✅ 완료 (2026-05-31)

---

## R4 — `Object[]` 프로젝션 타입 안전성 없음

### 현재 코드

```java
// PlaceInteractionLogRepository.java
@Query("""
    SELECT p.locationIdx, COUNT(p) AS cnt
    FROM PlaceInteractionLog p
    WHERE p.locationIdx IN :locationIds
      AND p.createdAt >= :since
    GROUP BY p.locationIdx
    """)
List<Object[]> countByLocationIdsSince(...);

// PlaceInteractionService.java — 인덱스 기반 접근
r -> (Long) r[0],
r -> Math.min(Math.log10(((Number) r[1]).doubleValue() + 1) / Math.log10(1001), 1.0)
```

### 문제

쿼리 컬럼 순서가 바뀌거나 다른 쿼리로 교체할 때 컴파일 오류 없이 `ClassCastException`이 런타임에 발생한다.

### 개선 방향

```java
// 프로젝션 record 추가
public record LocationInteractionCount(Long locationIdx, Long count) {}

// Repository
@Query("""
    SELECT new com.linkup.Petory.domain.petRecommendation.repository.LocationInteractionCount(
        p.locationIdx, COUNT(p))
    FROM PlaceInteractionLog p
    WHERE p.locationIdx IN :locationIds
      AND p.createdAt >= :since
    GROUP BY p.locationIdx
    """)
List<LocationInteractionCount> countByLocationIdsSince(...);

// Service — 타입 안전하게 사용
return rows.stream().collect(Collectors.toMap(
        LocationInteractionCount::locationIdx,
        r -> Math.min(Math.log10(r.count() + 1.0) / Math.log10(1001), 1.0)
));
```

### 적용 내역

`LocationInteractionCount.java` record 신규 파일 생성. JPQL constructor expression 방식 적용. `PlaceInteractionService`의 `(Long) r[0]`, `((Number) r[1]).doubleValue()` 캐스팅을 `LocationInteractionCount::locationIdx`, `r.count()` 접근으로 교체.

### 상태: ✅ 완료 (2026-05-31)

---

## R5 — `PetIntentAnalyzeResponse`의 `@Data`

### 현재 코드

```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PetIntentAnalyzeResponse {
    private String intentDomain;
    private double confidence;
    // ...
}
```

### 문제

`@Data`는 `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor`를 모두 포함한다. Jackson 역직렬화 후 DTO가 불변이어야 하는데 Setter가 노출된다. `confidence`나 `intentDomain` 같은 분석 결과 필드가 외부 코드에서 실수로 변경될 수 있다.

### 개선 방향 및 적용 내역

```java
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PetIntentAnalyzeResponse {
    private String intentDomain;
    private String intent;
    private List<String> recommendedCategories;
    private double confidence;
    private List<String> keywords;
    private List<String> intentTags;
    private String urgency;
    private String message;
    private List<String> suggestedCategories;
}
```

### 상태: ✅ 완료 (2026-05-31)

---

## R6 — `intent_tags.yml` YAML 로드 사실상 미사용

### 현재 코드

```python
# tag_extractor.py
def _load():
    global _tag_map
    if _tag_map:
        return
    with open(DATA_PATH) as f:
        data = yaml.safe_load(f)
    _tag_map = data["tags"]  # 로드됨

def extract_tags(text, intent_domain):
    _load()
    ...
    # _tag_map은 딱 한 곳에서만 사용
    if not matched and intent_domain in _tag_map:
        matched.add(_tag_map[intent_domain][0])  # fallback default 1건만
```

### 문제

YAML 파일 전체를 로드하지만, 실제 태그 매칭은 하드코딩된 `_KO_TAG_MAP`에서 수행한다. YAML은 매칭이 완전히 실패했을 때 도메인별 첫 번째 태그를 넣는 fallback에서만 쓰인다.

### 개선 방향

**Option A** — YAML 제거, fallback을 코드로 관리:

```python
_DOMAIN_DEFAULT_TAGS = {
    "MEDICAL": "medical",
    "GROOMING": "grooming",
    "SUPPLIES": "supplies",
    # ...
}

def extract_tags(text, intent_domain):
    ...
    if not matched:
        default = _DOMAIN_DEFAULT_TAGS.get(intent_domain)
        if default:
            matched.add(default)
    return list(matched)
```

**Option B** — `_KO_TAG_MAP`도 YAML로 이관하여 일관성 확보:

```yaml
# intent_tags.yml
ko_to_en:
  귀: ear
  눈: eye
  피부: skin
  ...
domain_defaults:
  MEDICAL: medical
  GROOMING: grooming
```

Option A가 코드 단순화 측면에서 우선 권장.

### 상태: 🔵 미적용 — `_KO_TAG_MAP` 이관 작업 범위가 커 배포 전 별도 검토 예정

---

## R7 — `petType` 파라미터 무시 — 의도적 결정 미표기

### 현재 코드

```python
# pet_intent_router.py
def analyze(req: PetIntentAnalyzeRequest):
    intent, domain, confidence = classify(req.text)  # req.petType 사용 안 함
```

```python
# intent_classifier.py
def classify(text: str) -> Tuple[str, str, float]:
    ...  # petType 파라미터 없음
```

### 문제

Spring이 `petType`을 Python에 전달하지만, 분류 로직이 완전히 무시한다. "강아지가 밥을 안 먹어요"와 "고양이가 밥을 안 먹어요"가 동일하게 분류된다. MVP 결정인지 추후 확장 예정인지 코드에 표기가 없어 후속 개발자가 버그로 착각할 수 있다.

### 개선 방향

의도적 결정이라면 주석으로 명확히 표기:

```python
def classify(text: str) -> Tuple[str, str, float]:
    # petType은 MVP에서 사용하지 않는다. 이후 petType별 규칙/모델 분기 시 파라미터 추가 예정.
    rule_result = _classify_by_rule(text)
    ...
```

### 상태: ✅ 완료 (2026-05-31) — `intent_classifier.py` docstring에 추가

---

## R8 — 임베딩 모델 워밍업 없음

### 현재 동작

`jhgan/ko-sroberta-multitask` 모델은 첫 번째 API 요청 시 lazy 로드된다. 모델 크기에 따라 첫 요청이 10~30초 이상 걸릴 수 있다. 로컬 개발 환경에서 첫 분석 요청에서 timeout(Spring 3초)이 발생하고 Python 서버는 계속 로딩 중인 상황이 발생할 수 있다.

### 개선 방향

```python
# main.py
from contextlib import asynccontextmanager
from app.nlp.embedding_model import get_model

@asynccontextmanager
async def lifespan(app: FastAPI):
    get_model()  # 서버 시작 시 모델 pre-load
    yield

app = FastAPI(title=settings.app_name, lifespan=lifespan)
```

또는 FastAPI 구버전 호환:
```python
@app.on_event("startup")
async def warmup():
    import asyncio
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(None, get_model)
```

### 적용 내역

`lifespan` 방식 채택. `main.py`에 `asynccontextmanager` 기반 startup hook 추가, `run_in_executor`로 blocking model load를 async로 처리.

### 상태: ✅ 완료 (2026-05-31)

---

## R9 — Confidence Threshold 이중 정책 미표기

### 현재 상태

| 위치 | 값 | 의미 |
|------|-----|------|
| `petory-nlp-server/app/config.py` | `confidence_threshold = 0.45` | Python이 UNKNOWN 반환하는 하한선 |
| `UserPetIntentSignalService.java` | `CONFIDENCE_THRESHOLD = 0.6` | Spring이 signal을 저장하는 하한선 |

### 의도

Python 0.45 미만 → UNKNOWN 반환 → Spring이 signal 저장 안 함  
Python 0.45~0.59 → 분석 결과 반환 → Spring이 저장 거부  
Python 0.60 이상 → Spring이 저장

2-pass 필터링으로 signal 품질을 보호하는 의도적 설계다. 코드에 설명이 없어 후속 개발자가 두 값의 관계를 파악하기 어렵다.

### 개선 방향

```java
// UserPetIntentSignalService.java
/**
 * Python 서버의 confidence_threshold(0.45)보다 높게 설정하여 2-pass 품질 필터를 구성.
 * Python: 0.45 미만 → UNKNOWN 반환 (1차 필터)
 * Spring: 0.60 미만 → signal 저장 거부 (2차 필터)
 */
private static final double CONFIDENCE_THRESHOLD = 0.6;
```

### 적용 내역

`UserPetIntentSignalService.java`의 상수 위에 인라인 주석 추가. `config.py`에도 threshold 관계 주석 추가.

### 상태: ✅ 완료 (2026-05-31)

---

## 잘된 점 (변경 불필요)

- Python 서버 장애 시 fallback 2단계 (키워드 추론 → 기본 검색) ✓
- `@Async` + 이벤트로 NLP 분석이 사용자 응답 경로 차단 없음 ✓
- signal 원문 미저장 원칙 준수 ✓
- TTL 7일 + expiresAt 인덱스 설계 ✓
- Rule-based + Embedding hybrid, n-gram fallback ✓
- Python 3초 timeout + fallback ✓
- 의료 안전 문구 필수 포함 ✓
- 점수 가중치(place 0.35, tag 0.30, distance 0.20, rating 0.10, review 0.05) 계획서와 일치 ✓

---

*작성 기준: `domain/petRecommendation/**`, `petory-nlp-server/app/**` 코드 리뷰 2026-05-31*  
*관련 문서: `docs/troubleshooting/petRecommendation/pet-recommendation-bugs-2026-05-31.md`*
