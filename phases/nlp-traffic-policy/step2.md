# Step 2: LocationSearch NLP 호출 조건 필터 — 자연어 판단 + Redis TTL dedup

## 목표

현재 로그인 사용자가 keyword 있는 주변서비스 검색을 실행할 때마다 Python NLP 서버가 호출된다.  
검색어가 남아 있는 상태에서 카테고리/정렬/반경만 바꿔도 같은 keyword로 Python이 반복 호출된다.

이 step에서는 `PetIntentSignalEventListener.handle(LocationSearchPerformedEvent)` 안에  
세 단계 필터를 추가해 불필요한 Python 호출을 차단한다.

---

## 배경 / 설계 의도

```
현재 흐름
  LocationSearchPerformedEvent 수신
    -> keyword != null → Python 호출

목표 흐름
  LocationSearchPerformedEvent 수신
    -> [필터 1] 자연어 판단: length >= 7 AND 공백 포함 → 통과 시 계속
    -> [필터 2] Redis TTL dedup: nlp:loc-dedup:{userIdx}:{normalizedKeyword} 존재 → skip
    -> 통과한 경우만 Python 호출
```

**필터 위치 결정**: `LocationServiceService`(이벤트 발행자)가 아닌  
`PetIntentSignalEventListener`(이벤트 소비자) 안에 둔다.  
이유: LocationServiceService에 NLP/Redis 의존성이 침투하면 책임 분리가 깨진다.

---

## 판단 기준 상세

### 자연어 판단 (isNaturalLanguage)

```
"동물병원"           → length=5, 공백 없음 → 카테고리 검색 → 분석 안 함
"귀 치료"            → length=4, 공백 있음 → 너무 짧음 → 분석 안 함
"강아지 귀 긁어요"   → length=9, 공백 있음 → 자연어 → 분석 후보
"고양이가 밥을 안 먹어요" → length=14, 공백 있음 → 자연어 → 분석 후보
```

기준: `normalized.length() >= 7 AND normalized.contains(" ")`  
단순하지만 단일 카테고리 검색과 자연어 문장을 실용적으로 구분한다.

**MVP 휴리스틱 한계**: `"강아지가귀를긁어요"` 처럼 공백 없이 붙여 쓴 문장은 필터를 통과하지 못한다.  
이는 의도된 trade-off다. 목적은 과호출 방지이므로 놓치는 케이스보다 차단 효과가 중요하다.  
포트폴리오 설명 시 "공백 기반 MVP 휴리스틱, 과호출 방지 우선 설계"로 명시한다.

### keyword 정규화 (normalize)

```java
text.trim().toLowerCase().replaceAll("\\s+", " ")
```

- 앞뒤 공백 제거
- 소문자 변환 (한국어는 소문자 변환 영향 없음, 영문 포함 시 일관성)
- 중복 공백 단일 공백으로 축소

`"강아지 귀  긁어요"` → `"강아지 귀 긁어요"` (중복 공백 제거)  
`"동물병원 "` → `"동물병원"` (앞뒤 공백 제거)

### Redis TTL dedup

- 키: `nlp:loc-dedup:{userIdx}:{normalizedKeyword}`
- TTL: 10분
- 조작: `setIfAbsent` — 키가 없으면 저장 후 true 반환, 있으면 false 반환
- true이면 새 분석 진행, false이면 skip

사용할 RedisTemplate: `customStringRedisTemplate` (`RedisTemplate<String, String>`)  
이미 `RedisConfig`에 `@Bean`으로 등록되어 있음.

---

## 수정 파일 목록

| 파일 | 변경 종류 |
|------|----------|
| `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetIntentSignalEventListener.java` | 필터 로직 추가, Redis 의존성 주입 |

---

## 구현 상세

### PetIntentSignalEventListener.java 최종 형태

```java
package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.client.PetIntentClient;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import com.linkup.Petory.domain.petRecommendation.event.CareRequestCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.CommunityPostCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.LocationSearchPerformedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetIntentSignalEventListener {

    private final PetIntentClient            petIntentClient;
    private final UserPetIntentSignalService signalService;

    @Qualifier("customStringRedisTemplate")
    private final RedisTemplate<String, String> redisTemplate;

    private static final Duration LOC_DEDUP_TTL    = Duration.ofMinutes(10);
    private static final int      MIN_NL_LENGTH    = 7;
    private static final String   LOC_DEDUP_PREFIX = "nlp:loc-dedup:";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("petIntentExecutor")
    public void handle(CommunityPostCreatedEvent event) {
        analyze(event.getUserIdx(), "COMMUNITY", event.getPostId(), event.getText());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("petIntentExecutor")
    public void handle(CareRequestCreatedEvent event) {
        analyze(event.getUserIdx(), "CARE", event.getCareRequestId(), event.getText());
    }

    // LocationSearch: 자연어 판단 + Redis TTL dedup 적용
    @EventListener
    @Async("petIntentExecutor")
    public void handle(LocationSearchPerformedEvent event) {
        if (event.getUserIdx() == null) return;

        String keyword = event.getKeyword();

        // 필터 1: 자연어 판단 — 짧거나 공백 없는 단순 카테고리 검색어는 분석 안 함
        if (!isNaturalLanguage(keyword)) {
            log.debug("[SignalListener] Location 검색 — 자연어 아님, skip. keyword={}", keyword);
            return;
        }

        // 필터 2: Redis TTL dedup — 10분 내 같은 user + keyword 분석됨
        // fail-closed: Redis 장애 시 Location NLP 분석 생략
        // (추천 signal은 부가 기능이라 Redis 장애 때 Python까지 호출하지 않는 것이 더 안전)
        String dedupKey = LOC_DEDUP_PREFIX + event.getUserIdx() + ":" + normalize(keyword);
        try {
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", LOC_DEDUP_TTL);
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("[SignalListener] Location 검색 dedup — 10분 내 동일 keyword, skip. userIdx={}", event.getUserIdx());
                return;
            }
        } catch (Exception e) {
            log.warn("[SignalListener] Redis dedup 체크 실패 — Location NLP 분석 생략 (fail-closed). userIdx={} error={}",
                    event.getUserIdx(), e.getMessage());
            return;
        }

        analyze(event.getUserIdx(), "LOCATION_SEARCH", null, keyword);
    }

    private void analyze(Long userIdx, String sourceType, Long sourceId, String text) {
        try {
            Optional<PetIntentAnalyzeResponse> result = petIntentClient.analyze(text, null);
            result.ifPresent(analysis ->
                    signalService.saveIfConfident(userIdx, sourceType, sourceId, analysis));
        } catch (Exception e) {
            log.warn("[SignalListener] 분석 실패 — 원 액션에 영향 없음. sourceType={} error={}",
                    sourceType, e.getMessage());
        }
    }

    // 공백 collapse + trim + lowercase
    static String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    // 자연어 판단: 정규화 후 7자 이상 + 공백 포함
    static boolean isNaturalLanguage(String text) {
        if (text == null) return false;
        String n = normalize(text);
        return n.length() >= MIN_NL_LENGTH && n.contains(" ");
    }
}
```

---

## 기존 테스트 수정 필요

`PetIntentSignalEventListenerTest`가 `@ExtendWith(MockitoExtension.class)` + `@InjectMocks`를 쓰는데,  
이제 `RedisTemplate` 필드가 추가되므로 `@Mock` 선언이 필요하다.

```java
@Mock
@Qualifier("customStringRedisTemplate")
private RedisTemplate<String, String> redisTemplate;
```

그리고 LocationSearch 핸들러 테스트에 dedup/자연어 판단 케이스를 추가한다:

```java
@Test
void locationSearchHandler_shortKeyword_skipsAnalysis() {
    // "미용" (5자, 공백 없음) → isNaturalLanguage = false → Python 호출 안 함
    assertThat(PetIntentSignalEventListener.isNaturalLanguage("미용")).isFalse();
}

@Test
void locationSearchHandler_naturalLanguage_passesFilter() {
    assertThat(PetIntentSignalEventListener.isNaturalLanguage("강아지 귀 긁어요")).isTrue();
}

@Test
void normalize_collapsesWhitespace() {
    assertThat(PetIntentSignalEventListener.normalize("강아지  귀  긁어요"))
        .isEqualTo("강아지 귀 긁어요");
}
```

---

## Acceptance Criteria

```bash
# 1. 컴파일
./gradlew compileJava
# 기대: BUILD SUCCESSFUL

# 2. 기존 + 신규 테스트 전부 통과
./gradlew test --tests "com.linkup.Petory.domain.petRecommendation.*"
# 기대: 전체 PASSED (기존 19건 + 신규 3건 이상)

# 3. normalize/isNaturalLanguage 단위 테스트 직접 확인
# 기대: "미용" → false, "강아지 귀 긁어요" → true
```

---

## 주의사항

- `@RequiredArgsConstructor`는 `final` 필드를 생성자로 주입하므로 `@Qualifier`를 생성자에 적용해야 한다.  
  Lombok `@RequiredArgsConstructor`와 `@Qualifier` 조합은 직접 생성자를 작성하거나  
  `@Qualifier`를 필드에 + 생성자 직접 선언 방식을 사용한다.  

  권장 방식 (Lombok 사용 시):
  ```java
  // 필드에 @Qualifier 선언 후 생성자를 직접 작성
  @Qualifier("customStringRedisTemplate")
  private final RedisTemplate<String, String> redisTemplate;

  public PetIntentSignalEventListener(
          PetIntentClient petIntentClient,
          UserPetIntentSignalService signalService,
          @Qualifier("customStringRedisTemplate") RedisTemplate<String, String> redisTemplate) {
      this.petIntentClient = petIntentClient;
      this.signalService   = signalService;
      this.redisTemplate   = redisTemplate;
  }
  ```
  이 경우 `@RequiredArgsConstructor`는 제거한다.

- Redis 장애 정책: **fail-closed**. `setIfAbsent` 예외 시 Location NLP 분석을 생략한다.  
  이유: Redis 장애 상황에서 Python까지 같이 호출하면 부가 기능이 핵심 인프라 장애를 가중시킨다.  
  분석 생략은 추천 카드가 안 뜨는 것이므로 사용자 핵심 경험에 영향 없다.  
  게시글/케어 작성 이벤트는 Redis를 사용하지 않으므로 이 정책의 영향을 받지 않는다.

---

## 커밋 메시지 (참고)

```
feat(petRecommendation): Location 검색 NLP 호출 조건 필터 추가

자연어 판단(length>=7 + 공백 포함) + Redis TTL dedup(10분)으로
카테고리/정렬/반경 변경 시 동일 keyword 반복 Python 호출 차단
```
