# Signal이 사용자에게 닿지 않는다 — 두 가지 문제 프레임워크 (2026-06-10)

**도메인**: petRecommendation (의도 분석 기반 추천)  
**작성 배경**: ai-intent-analysis-transition-2026-06-10.md 검토 중 코드 비교 분석으로 발견  
**상태**: 🔍 문제 정의 완료 / 구현 대기

---

## 1. 이 문서가 생긴 이유 — 출발점

### 1.1 서비스 흐름 요약

Petory는 사용자가 게시글을 쓰거나, 케어 요청을 하거나, 위치 검색을 할 때 그 텍스트를 Python NLP 서버로 보내 의도를 분석한다. 분석 결과가 충분히 확실하면 `user_pet_intent_signal`에 저장하고, 이후 사용자가 `GET /api/pet-recommend/signals`를 조회하면 주변 서비스 추천 카드를 응답한다.

이벤트 → 분석 → 저장 흐름:

```
CommunityPostCreatedEvent
CareRequestCreatedEvent          → PetIntentClient → Python NLP → saveIfConfident()
LocationSearchPerformedEvent                                         ↓
                                                          user_pet_intent_signal (DB)
                                                                     ↓
                                                          GET /api/pet-recommend/signals
                                                                     ↓
                                                          추천 카드 (프론트 폴링 시에만)
```

### 1.2 AI 전환 문서를 쓰게 된 이유

Python NLP 서버의 판단 로직(`intent_classifier.py`, `urgency_rules.py`, `tag_extractor.py`)은 하드코딩된 규칙으로 작동한다.

```python
# intent_classifier.py — _RULES 일부
{
  "intent": "pet_health_consult",
  "keywords": ["아파", "구토", "설사", "발열", ...],
  "confidence": 0.75,
  "domain": "MEDICAL"
}
```

- 9개 intent, 규칙마다 키워드 목록을 직접 작성
- 규칙에 없으면 embedding 유사도로 fallback (`jhgan/ko-sroberta-multitask`)
- urgency 판단은 순수 substring 매칭 (`kw in text`) — "피"가 "피부", "피곤"에도 매칭

사용자는 비유·맥락·새로운 표현으로 말한다. "요즘 애가 기운이 없어요", "밥을 잘 안 먹네요" 같은 표현은 규칙에 없으면 누락되거나 오판된다. 서비스가 성장할수록 규칙을 계속 추가해야 하는데, 이 방식은 유지비용이 선형으로 증가한다. 그래서 LLM으로 판단을 위임하는 방향을 검토하게 됐다.

---

## 2. 두 가지 문제 — 판단 문제 + 연결 문제

코드를 비교 분석하다가 더 근본적인 UX 문제가 드러났다. 판단 정확도를 올려도 해결되지 않는 문제가 있었다.

### 2.1 판단 문제 (원래 문서의 주제)

**"NLP가 사용자의 의도를 제대로 판단하지 못한다."**

현상:

- 규칙 기반 분류는 키워드가 없으면 miss
- urgency 판단은 substring 매칭이라 오탐/미탐 발생
- 9개 도메인 전체에 동일한 문제 (MEDICAL만의 문제가 아님)
- Java의 `inferCategoryFromKeyword()`와 Python의 `_RULES`가 각각 별도로 유지되는 두 벌의 규칙 세트

방향: LLM(GPT-4o-mini 등)에 판단을 위임하면 새 표현·비유·문맥을 추가 규칙 없이 처리 가능.

### 2.2 연결 문제 (이번 분석으로 발견)

**"signal이 저장되더라도 사용자가 직접 화면을 열지 않으면 닿지 않는다."**

현재 흐름:

```
사용자: "강아지가 구토를 해요" (게시글 작성)
  → signal 저장됨 ✅
  → 사용자는 아무것도 못 받음 ❌
  → 직접 추천 탭을 열어야만 카드가 보임
```

시나리오: 반려동물이 아프거나 이상 징후가 보여서 게시글을 올렸다. 그 순간 사용자는 불안하고 빠른 도움이 필요하다. 그런데 시스템은 signal을 저장하고 아무런 피드백도 주지 않는다. 사용자는 추천 탭이 있는지도 모를 수 있다.

**판단을 100% 정확하게 해도 연결이 없으면 사용자에게 가치가 전달되지 않는다.**

---

## 3. 연결 문제 — 해결 방향

### 3.1 웹 기준 (현재 대상 플랫폼)

앱은 현재 개발 상태가 불완전하다. 대상 플랫폼은 **웹(SSE 기반)**.

### 3.2 이미 있는 인프라

```java
// NotificationSseService.java
ConcurrentHashMap<Long, SseEmitter>  // userId → emitter

// NotificationService.java
createNotification()
  → DB 저장
  → Redis 캐시 (최신 50개, TTL 24h)
  → SSE push
  → FCM push (Firebase 미설정 또는 토큰 없음이면 발송 no-op, 단 Firebase가 켜져 있으면 사용자/토큰 조회 쿼리는 발생)
```

SSE 알림 파이프라인은 이미 구축되어 있다. 현재는 댓글 알림 용도로만 쓰이고 있다.

```java
// NotificationType.java — 현재
CARE_REQUEST_COMMENT
BOARD_COMMENT
MISSING_PET_COMMENT
```

### 3.3 전체 방향 (최종 목표)

signal이 저장되는 모든 경우를 사용자에게 연결한다. 3개 이벤트 소스 × 9개 도메인 전체:

| 이벤트 소스                  | 도메인 예시                  | 연결 방식                |
| ---------------------------- | ---------------------------- | ------------------------ |
| CommunityPostCreatedEvent    | MEDICAL, GROOMING 등         | 알림 또는 인앱 카드 갱신 |
| CareRequestCreatedEvent      | MEDICAL, DAYCARE_BOARDING 등 | 알림 또는 인앱 카드 갱신 |
| LocationSearchPerformedEvent | WALK_OUTING, CAFE_DINING 등  | 알림 또는 인앱 카드 갱신 |

단, 전체 도메인 알림은 알림 피로도 문제가 있다. 클릭률·사용성 확인 후 단계적으로 확장한다.

### 3.4 MVP 범위 (우선 구현)

**MEDICAL + HIGH urgency signal에 한해 즉시 알림 발송.**  
나머지 도메인은 기존 추천 카드 노출(`GET /api/pet-recommend/signals`) 유지.

#### MVP 구현 항목

1. `NotificationType.PET_HEALTH_ALERT` 추가

2. `saveIfConfident()` 저장 성공 후 MEDICAL+HIGH인 경우에만 알림 생성  
   → 저장 트랜잭션과 분리해야 한다. SSE는 커밋 전 발송되면 안 됨.  
   → `@TransactionalEventListener(AFTER_COMMIT)` 분리 또는 별도 `@Async` 메서드 사용

3. `Navigation.js` `handleNotificationClick()` 에 `PET_HEALTH_ALERT` 케이스 추가  
   → 클릭 시 `unified-map` 탭 이동 + 동물병원 카테고리 선택

#### 현재 Navigation.js 상태 (수정 필요)

```js
// Navigation.js:215 — 현재
if (notification.relatedType === 'BOARD') ...
else if (notification.relatedType === 'MISSING_PET') ...
else if (notification.relatedType === 'CARE_REQUEST') ...
// PET_HEALTH_ALERT → 아무 일도 안 일어남 (클릭해도 드롭다운만 닫힘)
```

#### 주의 사항

- `createNotification()`은 FCM까지 호출한다. 웹 사용자는 FCM 토큰이 없어 no-op이지만, DB 쿼리가 추가된다. 추후 웹 전용 알림 메서드 분리 고려.
- `PET_RECOMMENDATION` (일반 도메인) 알림은 MVP에서 제외. 알림 피로도 평가 후 확장.

---

## 4. 판단 문제 — 현재 상태와 LLM 전환 검토

### 4.1 현재 규칙의 취약점 정리

| 컴포넌트                                 | 취약점                                                                |
| ---------------------------------------- | --------------------------------------------------------------------- |
| `_RULES` (intent_classifier)             | 키워드 없는 표현 미분류, 새 표현 추가마다 코드 수정 필요              |
| `_HIGH_URGENCY_KEYWORDS` (urgency_rules) | 순수 substring — "피" → "피부"·"피곤" 오탐                            |
| `_LOW_URGENCY_DOMAINS`                   | SUPPLIES·FOOD_SNACK 등 6개 도메인 자동 LOW — 도메인 내 긴급 상황 무시 |
| `_KO_TAG_MAP` (tag_extractor)            | 형태소 → 태그 하드코딩, 커버리지 제한                                 |
| `inferCategoryFromKeyword()` (Java)      | Python `_RULES`와 별도로 유지되는 두 번째 규칙 세트                   |

### 4.2 embedding이 이미 일부 커버한다

`_RULES` miss 시 `jhgan/ko-sroberta-multitask`로 `intent_examples.yml` centroid와 코사인 유사도를 계산한다. 새로운 표현도 의미적으로 가까우면 분류된다. 즉 **LLM 전환 없이도 어느 정도 커버**된다.

문제가 되는 구간:

- urgency 판단: embedding 미사용, 순수 규칙
- tag 추출: embedding 미사용, 형태소 매핑
- Java fallback `inferCategoryFromKeyword()`: 완전히 별도 규칙

### 4.3 LLM 전환 비용-편익

**편익**: 비유·맥락·신조어 처리, 규칙 유지 부담 제거, urgency 판단 개선  
**비용**: 레이턴시 증가(동기 호출 시), API 비용, 환경 추가 설정 필요

**현실적 판단**: 전면 교체보다 urgency 판단부터 부분 교체가 더 낮은 위험.  
급하지 않은 도메인(SUPPLIES, FOOD_SNACK 등)은 현재 embedding으로도 충분.  
MEDICAL urgency 오판은 실제 피해로 이어질 수 있어 개선 우선순위가 높다.

---

## 5. 우선순위 제안

### MVP (즉시 구현)

| 순서 | 작업                                                      | 이유                                                  |
| ---- | --------------------------------------------------------- | ----------------------------------------------------- |
| 1    | `NotificationType.PET_HEALTH_ALERT` 추가                  | 타입 없으면 아무것도 못 함                            |
| 2    | MEDICAL+HIGH signal 저장 후 알림 생성 (after-commit 분리) | 연결 문제 해결 — 판단 정확도와 무관하게 즉시 UX 개선  |
| 3    | `Navigation.js` `PET_HEALTH_ALERT` 클릭 핸들러 추가       | 알림이 나가도 클릭 시 아무 일도 안 일어나면 의미 없음 |

### 이후 확장

| 순서 | 작업                                                      | 이유                                                                      |
| ---- | --------------------------------------------------------- | ------------------------------------------------------------------------- |
| 4    | urgency 판단 개선 (MEDICAL 한정 semantic 규칙 또는 LLM)   | 오판이 실제 피해로 이어질 수 있음                                         |
| 5    | `inferCategoryFromKeyword()` 제거 또는 Python 규칙과 통합 | 두 벌 규칙 유지비용 제거                                                  |
| 6    | `SignalInteractionLog` 실제 저장 연동                     | 현재 엔티티·레포지토리만 있고 저장 코드 없음 — 없으면 알림 효과 측정 불가 |
| 7    | 일반 도메인 `PET_RECOMMENDATION` 알림 확장                | 클릭률·알림 피로도 데이터 보고 결정                                       |
| 8    | 전체 intent 분류 LLM 전환                                 | 편익 있지만 비용-위험 큼 — 점진적 접근 권장                               |

---

## 6. 관련 파일

| 파일                                                                      | 역할                                   |
| ------------------------------------------------------------------------- | -------------------------------------- |
| `backend/.../petRecommendation/service/PetIntentSignalEventListener.java` | 3개 이벤트 → 분석 → saveIfConfident()  |
| `backend/.../petRecommendation/service/UserPetIntentSignalService.java`   | 저장 정책, threshold, TTL, cardMessage |
| `backend/.../petRecommendation/client/PetIntentClient.java`               | Java→Python HTTP 호출, petType 정규화  |
| `backend/.../notification/service/NotificationService.java`               | SSE+DB+Redis 알림 파이프라인           |
| `backend/.../notification/entity/NotificationType.java`                   | 현재 댓글 타입만 정의됨                |
| `petory-nlp-server/app/nlp/intent_classifier.py`                          | 규칙+embedding 분류                    |
| `petory-nlp-server/app/rules/urgency_rules.py`                            | urgency 판단 (취약 구간)               |
| `petory-nlp-server/app/nlp/tag_extractor.py`                              | 태그 추출                              |

## 7. 관련 문서

- [`ai-intent-analysis-transition-2026-06-10.md`](ai-intent-analysis-transition-2026-06-10.md) — LLM 전환 상세 계획 (Phase 0~3)
- [`docs/troubleshooting/petRecommendation/pettype-422-silent-drop-2026-06-10.md`](../../troubleshooting/petRecommendation/pettype-422-silent-drop-2026-06-10.md) — petType 422 무음 드롭 버그 수정 기록
