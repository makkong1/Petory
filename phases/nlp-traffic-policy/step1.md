# Step 1: petIntentExecutor — bounded ThreadPoolTaskExecutor 분리

## 목표

현재 `@EnableAsync` + 커스텀 풀 없음 → Spring Boot 기본값 (`core=8, queue=Integer.MAX_VALUE`) 의존.  
NLP 분석 작업이 이메일, 알림, 채팅방 생성 등 다른 `@Async` 작업과 같은 풀을 공유한다.

이 step에서는:
1. `PetIntentAsyncConfig` — `petIntentExecutor` 전용 bounded 풀 생성
2. `PetIntentSignalEventListener` — `@Async` → `@Async("petIntentExecutor")` 적용

---

## 배경 / 설계 의도

```
현재
  @Async (qualifier 없음)
    → Spring Boot 기본 executor
    → core=8, queue=무제한, max=무제한
    → NLP 백로그가 무제한으로 쌓일 수 있음
    → 다른 @Async 작업과 자원 공유

목표
  @Async("petIntentExecutor")
    → core=2, max=6, queue=500
    → 큐 포화 시 discard + warn log (추천 signal 부가기능이므로 허용)
    → thread name prefix = "pet-intent-" (로그/스레드 덤프 추적용)
    → 다른 비동기 작업과 격리

동작 순서 (ThreadPoolTaskExecutor 규칙):
  작업 유입
    → core(2) 미만이면 새 스레드 생성
    → core 이상이면 queue(500)에 적재
    → queue 포화 시 max(6)까지 추가 스레드 생성
    → max + queue 전부 포화 시 reject
  즉 max=6은 queue 500이 가득 찬 뒤에야 증가한다.
  평상시에는 core=2 스레드만 동작한다.
```

**discard 정책 부작용**: 큐가 포화되면 일부 사용자의 게시글/케어 요청에서 signal이 생성되지 않는다.  
추천 signal은 부가 기능이므로 이는 의도된 trade-off다. 핵심 요청(게시글/케어 저장)에는 영향이 없다.

---

## 수정 파일 목록

| 파일 | 변경 종류 |
|------|----------|
| `backend/main/java/com/linkup/Petory/global/config/PetIntentAsyncConfig.java` | 신규 생성 |
| `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetIntentSignalEventListener.java` | `@Async` qualifier 추가 |

---

## 구현 상세

### 1. PetIntentAsyncConfig.java 생성

패키지: `com.linkup.Petory.global.config`

```java
package com.linkup.Petory.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PetIntentAsyncConfig {

    /**
     * NLP 분석 전용 bounded executor.
     * 동작 순서: core(2) → queue(500) → max(6) → reject
     * queue 500이 먼저 차고, 그 이후에 max 6까지 스레드가 늘어난다.
     * 평상시에는 core=2 스레드만 동작한다.
     *
     * reject 시: 추천 signal 생성을 포기한다.
     * 추천 signal은 부가 기능이므로 핵심 요청(게시글/케어 저장)에는 영향 없음.
     */
    @Bean("petIntentExecutor")
    public Executor petIntentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("pet-intent-");
        executor.setRejectedExecutionHandler(new DiscardWithWarnPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Java 기본 DiscardPolicy는 로그를 남기지 않는다.
     * 직접 구현해 warn 로그 후 폐기한다.
     * static inner class이므로 outer @Slf4j의 log 필드에 접근 불가 → 별도 Logger 선언.
     */
    static class DiscardWithWarnPolicy implements RejectedExecutionHandler {
        private static final Logger log = LoggerFactory.getLogger(DiscardWithWarnPolicy.class);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("[petIntentExecutor] NLP 분석 작업 폐기 — 큐 포화. active={} queued={}",
                    executor.getActiveCount(), executor.getQueue().size());
            // 작업 폐기 — 추천 signal은 부가 기능, 핵심 요청에 영향 없음
        }
    }
}
```

### 2. PetIntentSignalEventListener.java 수정

세 핸들러 모두 `@Async` → `@Async("petIntentExecutor")` 변경:

```java
// Before
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void handle(CommunityPostCreatedEvent event) { ... }

// After
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("petIntentExecutor")
public void handle(CommunityPostCreatedEvent event) { ... }
```

CareRequestCreatedEvent, LocationSearchPerformedEvent 핸들러도 동일하게 변경.

---

## Acceptance Criteria

```bash
# 1. 컴파일
./gradlew compileJava
# 기대: BUILD SUCCESSFUL

# 2. 어노테이션 검증 테스트
./gradlew test --tests "com.linkup.Petory.domain.petRecommendation.service.PetIntentSignalEventListenerTest"
# 기대: 3/3 PASSED

# 3. 빈 등록 확인 (선택)
./gradlew test --tests "com.linkup.Petory.global.config.PetIntentAsyncConfigTest"
# 기대: PASSED
```

---

## 주의사항

- `PetIntentAsyncConfig`은 `global/config/` 패키지에 둔다 (`global/security/` 아님).
- `@EnableAsync`는 `PetoryApplication.java`에 이미 있으므로 추가하지 않는다.
- `DiscardWithWarnPolicy`는 `PetIntentAsyncConfig` 내부 static class로 정의한다 (별도 파일 불필요).

---

## 커밋 메시지 (참고)

```
feat(petRecommendation): petIntentExecutor bounded 스레드 풀 분리

core=2, max=6, queue=500, discard+warn 정책
NLP 분석 작업을 다른 @Async 작업과 격리하고 백로그 무제한 누적 방지
```
