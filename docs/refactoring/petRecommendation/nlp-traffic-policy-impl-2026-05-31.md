# petRecommendation — NLP 호출 정책 구현 결과 (2026-05-31)

**작업 배경**: `pet-recommendation-nlp-traffic-policy-2026-05-31.md` 설계 문서 기반 구현  
**범위**: petIntentExecutor bounded 풀 분리 + LocationSearch NLP 호출 조건 필터

---

## 구현 내역

### Step 1 — petIntentExecutor bounded ThreadPoolTaskExecutor

**신규 파일**: `global/config/PetIntentAsyncConfig.java`

```
설정값
  corePoolSize  = 2   (평시 스레드 수)
  maxPoolSize   = 6   (queue 포화 후 최대)
  queueCapacity = 500 (backlog 흡수)
  threadPrefix  = "pet-intent-"
  reject 정책   = DiscardWithWarnPolicy (log.warn + 폐기)

동작 순서: core(2) → queue(500) → max(6) → reject
  → queue 500이 먼저 찬 뒤에야 max까지 스레드 증가
  → 500ms/task 기준 약 125초 분량의 backlog를 core=2로 흡수
  → 극단적 버스트에서만 max=6 + reject 발동
```

**수정**: `PetIntentSignalEventListener` 세 핸들러 `@Async` → `@Async("petIntentExecutor")`

| 이전 | 이후 |
|------|------|
| Spring Boot 기본 executor 공유 (core=8, queue=무제한) | petIntentExecutor 전용 (core=2, queue=500, bounded) |
| 다른 @Async 작업과 자원 공유 | 알림/채팅/이메일 등과 격리 |
| 큐 포화 시 정책 없음 | DiscardWithWarnPolicy: warn 로그 후 폐기 |

**discard 정책 부작용 (의도된 trade-off)**:  
큐 포화 시 일부 게시글/케어의 signal이 생성되지 않는다.  
추천 signal은 부가 기능이므로 허용된다. 핵심 요청(게시글/케어 저장)에는 영향 없음.

**주의**: Java 기본 `DiscardPolicy`는 로그를 남기지 않는다.  
`DiscardWithWarnPolicy` static inner class에서 `LoggerFactory.getLogger()`를 직접 선언해 warn 로그 확보.  
(outer `@Slf4j` log 필드는 static inner class에서 접근 불가)

---

### Step 2 — LocationSearch NLP 호출 조건 필터

**수정 파일**: `PetIntentSignalEventListener.handle(LocationSearchPerformedEvent)`

기존에는 로그인 사용자 + keyword 있음 조건만으로 Python이 호출됐다.  
카테고리/정렬/반경 변경만으로도 동일 keyword가 반복 분석되는 문제를 두 단계 필터로 차단한다.

#### 필터 1: 자연어 판단 (isNaturalLanguage)

```java
static boolean isNaturalLanguage(String text) {
    String n = normalize(text);
    return n.length() >= 7 && n.contains(" ");
}
```

| 입력 | 결과 | 이유 |
|------|------|------|
| `"동물병원"` | false | 공백 없음 |
| `"귀 치료"` | false | 4자, 공백 있지만 너무 짧음 |
| `"강아지 귀 긁어요"` | true | 9자, 공백 포함 |
| `"고양이가 밥을 안 먹어요"` | true | 14자, 공백 포함 |

**MVP 휴리스틱 한계**: `"강아지가귀를긁어요"` 같은 공백 없이 붙여 쓴 문장은 통과하지 못한다.  
목적이 과호출 방지이므로 허용 가능한 trade-off. 포트폴리오 설명 시 "공백 기반 MVP 휴리스틱" 명시.

#### keyword 정규화 (normalize)

```java
static String normalize(String text) {
    return text.trim().toLowerCase().replaceAll("\\s+", " ");
}
```

- `"강아지  귀  긁어요"` → `"강아지 귀 긁어요"` (중복 공백 단일화)
- `"동물병원 "` → `"동물병원"` (앞뒤 공백 제거)

#### 필터 2: Redis TTL dedup

```
키  : nlp:loc-dedup:{userIdx}:{normalizedKeyword}
TTL : 10분
조작: setIfAbsent — false 반환 시 skip
```

같은 사용자가 10분 내 동일 keyword를 재검색해도 Python 호출 없이 skip.

#### Redis 장애 정책: fail-closed

```java
try {
    Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", LOC_DEDUP_TTL);
    if (Boolean.FALSE.equals(isNew)) { return; }
} catch (Exception e) {
    log.warn("[SignalListener] Redis dedup 체크 실패 — Location NLP 분석 생략 (fail-closed)...");
    return;  // Python 호출 안 함
}
```

Redis 장애 시 Python까지 같이 호출하지 않는다 (fail-closed).  
추천 signal은 부가 기능이므로 분석 생략이 더 안전한 선택.

게시글/케어 이벤트는 Redis를 사용하지 않으므로 이 정책의 영향을 받지 않는다.

---

## 테스트 결과

| 테스트 클래스 | 케이스 | 결과 |
|-------------|--------|------|
| `PetIntentSignalEventListenerTest` | 16건 (어노테이션 4, 자연어 5, normalize 3, 이벤트 처리 4) | 전부 PASS |
| `PetRecommendationControllerValidationTest` | 3건 | 전부 PASS |
| `UserPetIntentSignalServiceTest` | 8건 | 전부 PASS |
| `PlaceInteractionServiceTest` | 6건 | 전부 PASS |
| **합계** | **33건** | **32/32 PASS** |

---

## 변경 파일 목록

| 파일 | 변경 |
|------|------|
| `global/config/PetIntentAsyncConfig.java` | 신규 |
| `domain/petRecommendation/service/PetIntentSignalEventListener.java` | @Async qualifier, Redis 주입, LocationSearch 필터 추가 |
| `test/.../PetIntentSignalEventListenerTest.java` | 신규 케이스 추가 (Redis mock, 자연어 판단, dedup, fail-closed) |
| `phases/nlp-traffic-policy/index.json` | status completed |

---

## 관련 문서

- 설계: `docs/refactoring/petRecommendation/pet-recommendation-nlp-traffic-policy-2026-05-31.md`
- 구현 계획: `phases/nlp-traffic-policy/step1.md`, `step2.md`
