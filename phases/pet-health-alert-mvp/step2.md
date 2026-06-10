# Step 2 — SignalSavedEvent 생성 + saveIfConfident() publish

## 목적
`saveIfConfident()` 트랜잭션 안에서 `ApplicationEventPublisher.publishEvent()`를 호출하면,
Spring이 트랜잭션 커밋 이후(`AFTER_COMMIT`) 리스너를 발동시킨다.
이 방식으로 SSE가 DB 커밋 전에 나가는 side effect를 방지한다.

## 주의
`@Async`만으로는 after-commit이 보장되지 않는다.
올바른 패턴: `saveIfConfident()` 내부에서 event publish → Step 3의 `@TransactionalEventListener(AFTER_COMMIT)` 에서 알림 생성.

---

## 신규 파일 1 — SignalSavedEvent.java

**경로**: `backend/main/java/com/linkup/Petory/domain/petRecommendation/event/SignalSavedEvent.java`

```java
package com.linkup.Petory.domain.petRecommendation.event;

public record SignalSavedEvent(
        Long userIdx,
        Long signalId,
        String intentDomain,
        String urgency
) {}
```

---

## 수정 파일 — UserPetIntentSignalService.java

**경로**: `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java`

### 변경 1: 생성자에 ApplicationEventPublisher 주입

현재 생성자 (Lombok `@RequiredArgsConstructor` 사용):
```java
private final UserPetIntentSignalRepository signalRepository;
private final ObjectMapper objectMapper;
```

변경 후:
```java
private final UserPetIntentSignalRepository signalRepository;
private final ObjectMapper objectMapper;
private final org.springframework.context.ApplicationEventPublisher eventPublisher;
```

`@RequiredArgsConstructor`가 자동 생성하므로 import 추가 외 생성자 코드 변경 불필요.

### 변경 2: signalRepository.save() 직후 이벤트 publish

현재 코드 (saveIfConfident 메서드 내 try 블록):
```java
signalRepository.save(signal);
log.info("[Signal] 저장 완료 userIdx={} ...", ...);
```

변경 후:
```java
UserPetIntentSignal saved = signalRepository.save(signal);
log.info("[Signal] 저장 완료 userIdx={} ...", ...);
eventPublisher.publishEvent(new SignalSavedEvent(
        userIdx,
        saved.getId(),
        analysis.getIntentDomain(),
        analysis.getUrgency()
));
```

`UserPetIntentSignal` 엔티티에 `getId()` 메서드가 있는지 확인 필요.
없으면 `@Getter` + `@Id` 필드 확인 후 getter 이름 맞출 것.

---

## AC (Acceptance Criteria)
```bash
./gradlew compileJava
```
컴파일 오류 없이 통과해야 한다.
`SignalSavedEvent` record가 존재하고, `UserPetIntentSignalService`에서 import되어야 한다.
