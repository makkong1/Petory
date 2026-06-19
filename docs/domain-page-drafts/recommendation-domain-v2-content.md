# RecommendationDomainV2 페이지 드래프트 점검 및 작성

> 목적: 포트폴리오 repo의 `RecommendationDomainV2.jsx` 현재 내용과 Petory 코드/문서를 비교해, 실제 페이지에 넣을 문구와 수정 포인트를 확정한다.  
> 작성 기준: 현재 구현을 1순위로 보고, `docs/domains/recommendation.md`, petRecommendation 리팩토링/트러블슈팅 문서, `petory-nlp-server` 코드는 근거와 맥락으로만 사용한다.

---

## 0. 판단

현재 첨부한 `RecommendationDomainV2.jsx`는 구조와 방향이 좋다. Recommendation 페이지는 "새 장소 추천 DB를 만들었다"가 아니라, 사용자의 반려생활 입력을 NLP signal로 바꾸고 기존 Location 검색의 카테고리 진입점으로 연결했다는 점을 중심에 두는 게 맞다.

그대로 진행해도 되는 부분:

- 커뮤니티 글, 케어 요청, 주변서비스 검색어를 이벤트로 수집한다는 설명
- Spring 이벤트와 `@Async("petIntentExecutor")`로 본 기능 트랜잭션과 분리했다는 설명
- `petory-nlp-server`의 rule + embedding 분석 구조
- 원문 텍스트는 저장하지 않고 intent, category, confidence, tags, urgency만 저장한다는 설명
- 주변서비스 탭은 `/api/pet-recommend/signals`로 추천 카드를 받고, 카드 클릭 시 Location category 검색으로 연결한다는 설명
- Location 검색 NLP 호출은 자연어 휴리스틱과 Redis 10분 dedup으로 제어한다는 설명
- NLP 장애가 게시글 작성, 케어 요청, 위치 검색을 막지 않는다는 한계/장애 처리 설명

보완하면 좋은 부분:

- JSX의 "Spring 0.60 필터"와 "TTL 7일"은 현재 코드 기준으로 단순화된 표현이다. 현재 저장 threshold와 TTL은 도메인·urgency별로 다르다.
- `/api/pet-recommend` 즉시 시설 추천 API는 백엔드에 있지만, 현재 프론트 `petRecommendationApi.js`는 `/signals`만 감싼다. 주 사용자 경로는 추천 카드 signal 중심이다.
- `GET /api/pet-recommend`는 메서드에 `@PreAuthorize`가 없지만, `SecurityConfig`의 `/api/**.authenticated()` 때문에 실제 API 접근은 인증 대상이다. `/signals`, `/interact`는 메서드 레벨 `@PreAuthorize`도 있다.
- `SignalSavedEvent`와 `PetHealthAlertNotificationHandler`를 통해 `MEDICAL + HIGH` signal은 건강 알림으로 이어진다. 페이지에 넣으면 추천 도메인의 사용자 피드백 흐름이 더 분명하다.
- `SignalInteractionLog`는 엔티티/테이블/레포가 있지만, 현재 추천 카드 클릭 저장 로직은 없다. 한계 섹션에 두면 좋다.
- `petType`은 Java에서 `DOG/CAT/OTHER`로 정규화되어 Python까지 전달되지만, 현재 Python 분류 로직에서는 아직 사용하지 않는다.

---

## 1. 페이지 상단

### H1

Recommendation 도메인

### 소개 문단

Recommendation 도메인은 커뮤니티 글, 케어 요청, 주변서비스 검색어처럼 사용자의 최근 반려생활 입력을 분석해 주변서비스 탭에 추천 카드를 보여 주는 기능이다. 새로운 장소 조회 시스템을 만들지 않고, 기존 Location 검색 위에 "근처 동물병원 보기" 같은 카테고리 진입점을 얹는다. 입력 수집은 Spring 이벤트로 분리하고, Python NLP 서버가 intent domain, 추천 카테고리, confidence, tags, urgency를 분석한다. 저장 단계에서는 원문 텍스트를 남기지 않고 요약 signal만 저장하며, 분석이 늦거나 실패해도 글 작성·케어 요청·검색은 그대로 성공한다.

### 핵심 기능 태그 (`corePillars`)

첨부 JSX의 8개 태그는 그대로 사용 가능하다.

```javascript
const corePillars = [
  '비동기 intent signal',
  'Python NLP 분석',
  '형태소 정밀 매칭',
  '원문 텍스트 미저장',
  '추천 카드 /signals',
  'Location 카테고리 연결',
  'NLP 호출·부하 제어',
  '본 기능 무영향 장애 처리',
];
```

선택적으로 건강 알림까지 강조하고 싶으면 `"MEDICAL HIGH 알림"`을 추가할 수 있다. 다만 카드 추천 페이지의 초점은 signal과 Location 연결이므로 현재 8개 구성이 더 균형이 좋다.

---

## 2. `section#intro` - 도메인 개요

### 2-1. 개요 카드 문구

Recommendation은 두 흐름으로 나뉜다. 첫 번째는 주 사용자 경로인 추천 카드 signal이다. 게시글, 케어 요청, 위치 검색어가 이벤트로 발행되면 비동기 listener가 `petory-nlp-server`의 `POST /api/pet-intent/analyze`를 호출하고, 충분히 신뢰할 수 있는 분석만 `user_pet_intent_signal`에 저장한다. 주변서비스 탭은 `GET /api/pet-recommend/signals`로 최신 유효 signal을 최대 10건 조회하고, 첫 번째 추천 카테고리를 `targetCategory`로 사용한다.

두 번째는 즉시 시설 추천 API다. `GET /api/pet-recommend?lat&lng&text&radius&petType`은 텍스트를 NLP 분석한 뒤 1순위 카테고리로 Location 후보를 조회하고, 인기도·태그 일치·거리·평점·리뷰 수를 점수화해 상위 시설을 반환한다. 다만 현재 프론트의 `petRecommendationApi.js`는 `/signals`만 감싸고 있으므로, 실제 화면 주 경로는 추천 카드 signal이다.

NLP 서버는 rule hit를 먼저 시도하고, rule miss일 때 `intent_examples.yml` centroid와 문장 embedding 유사도를 비교한다. 형태소 분석은 Kiwipiepy를 사용하고, 1~2음절 한글 키워드는 형태소 exact match로 처리해 `"귀신" -> ear`, `"눈사람" -> eye` 같은 부분 문자열 오탐을 줄인다.

### 2-2. 구조 테이블

| 항목 | 현재 코드 기준 |
|---|---|
| API | `PetRecommendationController`의 `/api/pet-recommend` |
| 실제 인증 정책 | `SecurityConfig`의 `/api/**.authenticated()`로 인증 필요 |
| signal 조회 | `GET /api/pet-recommend/signals`, 메서드 `@PreAuthorize` |
| interaction 기록 | `POST /api/pet-recommend/interact`, `VIEW/NAVIGATE/FAVORITE`만 허용 |
| 즉시 추천 API | `GET /api/pet-recommend?lat&lng&text&radius&petType` |
| 프론트 주 경로 | `petRecommendationApi.getSignals()`만 사용 |
| 입력 출처 | `COMMUNITY`, `CARE`, `LOCATION_SEARCH` |
| 커뮤니티/케어 이벤트 | `@TransactionalEventListener(AFTER_COMMIT)` |
| 위치 검색 이벤트 | 일반 `@EventListener`, 자연어 필터 + Redis 10분 dedup |
| NLP 호출 | `PetIntentClient` -> `petory-nlp-server` |
| NLP 장애 | `Optional.empty()` 후 signal skip 또는 즉시 추천 fallback |
| 저장 엔티티 | `UserPetIntentSignal`, 원문 텍스트 미저장 |
| 조회 상한 | 활성 signal 최신순 최대 10건 |
| 건강 알림 | `MEDICAL + HIGH` signal 저장 후 `PET_HEALTH_ALERT` 알림 |

### 2-3. 저장 정책 표

JSX의 `Python 0.45 / Spring 0.60` 표현은 아래처럼 최신 코드 기준으로 바꾸는 게 좋다.

| domain | urgency | Spring 저장 threshold |
|---|---|---:|
| `MEDICAL` | `HIGH` | 0.55 |
| `MEDICAL` | 그 외 | 0.65 |
| `FOOD_SNACK`, `SUPPLIES`, `WALK_OUTING`, `CAFE_DINING` | any | 0.45 |
| 그 외 | any | 0.60 |

TTL도 단일 7일이 아니다.

| 조건 | TTL |
|---|---:|
| `MEDICAL + HIGH` | 1일 |
| `MEDICAL` 그 외 | 3일 |
| `LODGING_TRAVEL` | 14일 |
| 그 외 | 7일 |

보조 설명:

- Python 1차 confidence threshold는 0.45다.
- Python threshold는 주로 embedding path에서 작동한다. rule hit confidence는 0.88~0.92 휴리스틱 값이다.
- Spring 저장 단계는 같은 `(userIdx, intentDomain)`의 유효 signal이 있으면 중복 저장하지 않는다.

### 2-4. 데이터 흐름 카드

문구:

Recommendation 시퀀스는 도메인 페이지에 모든 흐름을 반복하지 않고 통합 흐름 페이지로 분리한다. 게시글/케어/위치검색 이벤트 수집, Python NLP 분석, signal 저장, 추천 카드 노출, Location 카테고리 검색 연결을 한 번에 확인할 수 있게 한다.

내부 링크:

- `/domains/flows?tab=recommendation`
- `/domains/flows?tab=location`
- `/domains/flows?tab=notification`

---

## 3. `section#design` - 기술 결정

첨부 JSX의 A~G 구성은 좋다. 다만 C의 threshold/TTL과 F의 프론트 사용 여부를 현재 코드 기준으로 다듬으면 된다.

### A. 이벤트 기반 수집 - 본 기능을 막지 않음

핵심 문구:

추천 signal은 부가 기능이다. 게시글 작성, 케어 요청 생성, 위치 검색이 NLP 서버 상태에 묶이면 핵심 기능 안정성이 떨어진다. 현재 구현은 커뮤니티와 케어 이벤트를 원 트랜잭션 커밋 이후 처리하고, 위치 검색 이벤트는 조회성 이벤트로 별도 필터를 통과한 경우만 비동기로 처리한다.

코드 스니펫 후보:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("petIntentExecutor")
public void handle(CommunityPostCreatedEvent event) {
    analyze(event.getUserIdx(), "COMMUNITY", event.getPostId(), event.getText(), null);
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("petIntentExecutor")
public void handle(CareRequestCreatedEvent event) {
    analyze(event.getUserIdx(), "CARE", event.getCareRequestId(), event.getText(), event.getPetType());
}

@EventListener
@Async("petIntentExecutor")
public void handle(LocationSearchPerformedEvent event) {
    // 자연어 판단 + Redis dedup 후 analyze()
}
```

전용 executor:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(6);
executor.setQueueCapacity(500);
executor.setThreadNamePrefix("pet-intent-");
executor.setRejectedExecutionHandler(new DiscardWithWarnPolicy());
```

근거:

- `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetIntentSignalEventListener.java`
- `backend/main/java/com/linkup/Petory/global/config/PetIntentAsyncConfig.java`
- `docs/refactoring/petRecommendation/nlp-traffic-policy-impl-2026-05-31.md`

### B. Python NLP - rule first, embedding fallback

핵심 문구:

`petory-nlp-server`는 FastAPI로 분리되어 있고, Spring은 `PetIntentClient`로만 호출한다. 분석은 rule hit를 먼저 시도한다. rule miss일 때만 `jhgan/ko-sroberta-multitask` 기반 문장 embedding과 intent centroid의 dot product를 비교한다. 서버 시작 시 lifespan에서 embedding model과 centroid를 미리 warm-up해 첫 embedding path 요청 지연을 줄인다.

코드 스니펫 후보:

```python
def classify(text: str, pet_type: Optional[str] = None) -> Tuple[str, str, float]:
    _ = pet_type  # DOG/CAT rule branching reserved for future use
    rule_result = _classify_by_rule(text)
    if rule_result is not None:
        return rule_result
    _load()
    query_vec = encode([text])[0]
    scores = {
        intent: float(np.dot(query_vec, centroid))
        for intent, centroid in _intent_embeddings.items()
    }
    best_intent = max(scores, key=scores.get)
    return best_intent, _intent_domains[best_intent], scores[best_intent]
```

오탐 방지:

```python
elif len(keyword) <= 2 and keyword.isalpha():
    if _kw_tokens is None:
        _kw_tokens = set(extract_keywords(normalized))
    matched = keyword in _kw_tokens
```

응답 계약:

```json
{
  "intentDomain": "MEDICAL",
  "intent": "MEDICAL_CONCERN",
  "recommendedCategories": ["동물병원", "동물약국"],
  "confidence": 0.92,
  "keywords": ["강아지", "귀", "긁"],
  "intentTags": ["ear", "scratch"],
  "urgency": "NORMAL",
  "message": "ear, scratch 불편 표현이 감지되었습니다. ..."
}
```

근거:

- `petory-nlp-server/app/api/pet_intent_router.py`
- `petory-nlp-server/app/nlp/intent_classifier.py`
- `petory-nlp-server/app/nlp/tag_extractor.py`
- `petory-nlp-server/app/main.py`

### C. Signal 저장 - 원문 미저장, threshold/TTL 세분화

핵심 문구:

signal 저장은 개인정보와 노이즈를 줄이는 방향으로 설계했다. 게시글 본문, 케어 요청 내용, 위치 검색어 원문은 DB에 저장하지 않는다. 대신 intent domain, intent, 추천 카테고리 JSON, confidence, urgency, intent tags, 만료 시각만 저장한다. 저장 전에는 domain과 urgency에 맞는 threshold를 통과해야 하며, 같은 사용자에게 같은 intentDomain의 유효 signal이 있으면 중복 저장하지 않는다.

코드 스니펫 후보:

```java
private static final Map<String, Double> DOMAIN_THRESHOLDS = Map.of(
    "MEDICAL",     0.65,
    "FOOD_SNACK",  0.45,
    "SUPPLIES",    0.45,
    "WALK_OUTING", 0.45,
    "CAFE_DINING", 0.45
);
private static final double DEFAULT_THRESHOLD = 0.60;

private double thresholdFor(String domain, String urgency) {
    if ("MEDICAL".equals(domain) && "HIGH".equals(urgency)) {
        return 0.55;
    }
    return DOMAIN_THRESHOLDS.getOrDefault(domain, DEFAULT_THRESHOLD);
}
```

TTL:

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

중복 방지:

```java
if (signalRepository.existsByUserIdxAndIntentDomainAndExpiresAtAfter(
        userIdx, analysis.getIntentDomain(), LocalDateTime.now())) {
    return;
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java`
- `backend/main/java/com/linkup/Petory/domain/petRecommendation/entity/UserPetIntentSignal.java`
- `backend/main/java/com/linkup/Petory/domain/petRecommendation/repository/UserPetIntentSignalRepository.java`

### D. 프론트 카드 -> Location 검색

핵심 문구:

추천 카드는 장소 목록을 직접 내려주지 않는다. 주변서비스 탭이 `/signals`로 카드 데이터를 받고, 카드의 `targetCategory` 또는 `recommendedCategories[0]`를 Location 검색 카테고리로 넘긴다. 지도 중심, 반경, 정렬 상태는 기존 Location 화면 상태를 유지한다.

코드 스니펫 후보:

```javascript
const categoryName = signal.targetCategory || signal.recommendedCategories?.[0];
if (!categoryName) return null;

<SignalButton onClick={() => onSignalPick?.(categoryName)}>
  <SignalText>{signal.cardMessage || '최근 입력 기반 추천이 있어요.'}</SignalText>
  <SignalAction>{signal.actionLabel || `근처 ${categoryName} 보기`}</SignalAction>
</SignalButton>
```

API:

```javascript
export const petRecommendationApi = {
  getSignals: async () => {
    if (!getToken()) return [];
    try {
      const res = await api.get('/signals');
      return Array.isArray(res.data) ? res.data : [];
    } catch (error) {
      return [];
    }
  },
};
```

근거:

- `frontend/src/api/petRecommendationApi.js`
- `frontend/src/components/UnifiedMap/controls/LocationControls.js`
- `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`

### E. Location 검색 NLP 호출 정책

핵심 문구:

Location 검색은 카테고리, 반경, 정렬 변경만으로도 같은 검색어가 반복 호출될 수 있다. 그래서 위치 검색에서 발생하는 NLP 분석은 자연어처럼 보이는 검색어만 통과시키고, 같은 사용자와 같은 검색어는 Redis TTL 10분으로 중복 호출을 막는다. Redis 장애 시에는 fail-closed로 Python 호출을 생략한다.

코드 스니펫 후보:

```java
static boolean isNaturalLanguage(String text) {
    if (text == null) return false;
    String n = normalize(text);
    return n.length() >= MIN_NL_LENGTH && n.contains(" ");
}
```

Dedup:

```java
String dedupKey = "nlp:loc-dedup:" + event.getUserIdx() + ":" + normalize(keyword);
try {
    Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", LOC_DEDUP_TTL);
    if (Boolean.FALSE.equals(isNew)) {
        return;
    }
} catch (Exception e) {
    return; // fail-closed
}
```

예시:

```text
"동물병원" -> skip
"귀 치료" -> skip
"강아지 귀 긁어요" -> analyze 후보
"강아지가귀를긁어요" -> skip, MVP 휴리스틱 한계
```

### F. 점수 기반 장소 API - 카드와 별도

핵심 문구:

`GET /api/pet-recommend`는 추천 카드와 별도인 즉시 시설 추천 API다. 자연어와 좌표를 받아 NLP 분석을 수행하고, 1순위 추천 카테고리로 Location 후보를 조회한 뒤 `PetRecommendScoreCalculator`로 점수화한다. 현재 프론트 주 경로는 `/signals` 카드이므로, 이 API는 백엔드 기능과 확장 포인트로 설명하는 게 정확하다.

점수 가중치:

| 항목 | 가중치 |
|---|---:|
| place popularity | 0.35 |
| tag match | 0.30 |
| distance | 0.20 |
| rating | 0.10 |
| review count | 0.05 |

NLP 실패 fallback:

```java
if (analysisOpt.isEmpty()) {
    return fallbackRecommend(text, lat, lng, radius);
}
```

한계:

- fallback은 간단한 keyword 기반 카테고리 추정이다.
- fallback 결과는 점수 계산 없이 거리순 Location DTO를 반환한다.

### G. MEDICAL HIGH 건강 알림

현재 JSX에는 없지만 추가하면 좋은 카드다.

핵심 문구:

Recommendation signal은 주변서비스 카드뿐 아니라 알림과도 연결된다. `MEDICAL + HIGH` signal이 저장되면 `SignalSavedEvent`가 발행되고, `PetHealthAlertNotificationHandler`가 커밋 이후 비동기로 `PET_HEALTH_ALERT` 알림을 생성한다. 알림 실패는 signal 저장을 롤백하지 않는다.

코드 스니펫 후보:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("petIntentExecutor")
public void handle(SignalSavedEvent event) {
    if (!"MEDICAL".equals(event.intentDomain()) || !"HIGH".equals(event.urgency())) {
        return;
    }
    notificationService.createNotification(
            event.userIdx(),
            NotificationType.PET_HEALTH_ALERT,
            "반려동물 건강 알림",
            "위급할 수 있어요. 가까운 동물병원에 바로 문의하세요.",
            event.signalId(),
            "PET_INTENT_SIGNAL");
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetHealthAlertNotificationHandler.java`
- `backend/main/java/com/linkup/Petory/domain/petRecommendation/event/SignalSavedEvent.java`
- `docs/domains/recommendation.md`

---

## 4. `section#limits` - 한계와 운영 메모

첨부 JSX의 한계 섹션은 유지하되, 아래 항목으로 더 정확히 다듬으면 좋다.

- Python NLP 서버가 미기동이면 signal은 쌓이지 않는다. 게시글 작성, 케어 요청, 위치 검색은 정상 처리된다.
- `/signals` 프론트 호출은 토큰이 없으면 생략하고, 실패하면 빈 배열로 처리한다.
- `GET /api/pet-recommend` 즉시 시설 추천 API는 백엔드에 있지만 현재 프론트 API 래퍼는 `/signals`만 제공한다.
- `GET /api/pet-recommend`는 메서드 `@PreAuthorize`가 없지만, 현재 `SecurityConfig`의 `/api/**` catch-all 때문에 실제 접근은 인증 대상이다.
- `petType`은 Python까지 전달되지만 현재 분류 로직에서는 사용하지 않는다.
- Location 자연어 판단은 `길이 >= 7 && 공백 포함` 휴리스틱이다. 공백 없는 자연어는 분석 대상에서 빠질 수 있다.
- Redis dedup 장애 시 Location 검색 NLP는 fail-closed로 생략된다.
- `petIntentExecutor` queue 500 포화 시 일부 signal 분석은 폐기된다. 핵심 요청 보호를 우선하는 정책이다.
- `SignalInteractionLog`는 준비되어 있지만 추천 카드 클릭 저장 로직은 아직 없다.
- NLP confidence는 rule 경로와 embedding 경로의 의미가 다르므로 직접 비교하면 안 된다.
- 의료 관련 추천은 진단이 아니라 가까운 동물병원 문의를 안내하는 수준이다.

---

## 5. `section#docs` - 연결 문서와 소스

### 내부 페이지 링크

- `/domains/recommendation/optimization`
- `/domains/recommendation/refactoring`
- `/domains/location/v2`
- `/domains/notification`
- `/domains/flows?tab=recommendation`

### GitHub 소스 링크 후보

첨부 JSX의 링크는 대체로 좋다. 추가하면 좋은 링크:

```javascript
const PETORY_SIGNAL_LISTENER =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetIntentSignalEventListener.java';
const PETORY_SIGNAL_SERVICE =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java';
const PETORY_RECOMMEND_SERVICE =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetRecommendationService.java';
const PETORY_PET_INTENT_EXECUTOR =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/global/config/PetIntentAsyncConfig.java';
const PETORY_HEALTH_ALERT_HANDLER =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetHealthAlertNotificationHandler.java';
const PETORY_NLP_INTENT_CLASSIFIER =
  'https://github.com/makkong1/Petory/blob/dev/petory-nlp-server/app/nlp/intent_classifier.py';
const PETORY_NLP_TAG_EXTRACTOR =
  'https://github.com/makkong1/Petory/blob/dev/petory-nlp-server/app/nlp/tag_extractor.py';
```

### 문서 근거

- `docs/domains/recommendation.md`
- `docs/refactoring/petRecommendation/nlp-traffic-policy-impl-2026-05-31.md`
- `docs/refactoring/petRecommendation/pet-recommendation-nlp-traffic-policy-2026-05-31.md`
- `docs/troubleshooting/petRecommendation/nlp-server-issues-2026-06-09.md`
- `docs/troubleshooting/petRecommendation/pettype-422-silent-drop-2026-06-10.md`
- `docs/refactoring/petRecommendation/signal-to-user-gap-two-problems-2026-06-10.md`

---

## 6. JSX 반영 체크리스트

`RecommendationDomainV2.jsx`를 고칠 때 우선순위:

1. 전체 구조와 corePillars는 유지한다.
2. C 카드의 `TTL 7일`, `Spring 0.60` 표현을 domain/urgency별 threshold와 TTL 표로 최신화한다.
3. F 카드에 "점수 기반 장소 API는 백엔드에 있지만 현재 프론트 주 경로는 `/signals` 카드"라는 구분을 넣는다.
4. `MEDICAL + HIGH` signal이 `PET_HEALTH_ALERT` 알림으로 이어지는 카드를 추가하거나 한계/도메인 연결에 넣는다.
5. 인증 설명은 controller annotation이 아니라 `SecurityConfig /api/**` 실제 정책까지 같이 쓴다.
6. `petType`은 전달되지만 현재 Python 분류 로직 미사용이라는 한계를 유지한다.
7. `SignalInteractionLog` 미사용과 추천 카드 클릭 저장 미구현을 한계에 추가한다.
8. NLP 수치와 회귀 테스트 내용은 운영 절대 성능이 아니라 품질 개선/계약 정합 근거로 표현한다.
