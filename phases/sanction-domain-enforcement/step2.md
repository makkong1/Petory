# Step 2 — User 제재 적용 이벤트 인프라 (UserSanctionAppliedEvent + 발행)

## 목적

Care/Meetup/Chat 도메인이 제재 적용 시점에 후속 처리를 수행하려면 User 도메인에서 이벤트를 발행해야 한다. 이 step에서는 이벤트 클래스를 정의하고, `UserSanctionService`의 `addSuspension()`/`addBan()`에서 이벤트를 발행한다. 리스너 구현은 Step 4(Care), Step 5(Meetup)에서 한다.

## 배경 정책

- 이벤트는 Spring in-memory ApplicationEvent 기반. Outbox/MQ는 2차 과제.
- 제재 자체(DB 저장)가 커밋된 이후 이벤트를 발행해야 한다. 이벤트 처리 실패 시 제재는 롤백하지 않는다.
- 이벤트 발행은 `@Transactional` 메서드 내부 `publishEvent()` 호출로 충분하다. 리스너에서 `@TransactionalEventListener(phase = AFTER_COMMIT)`을 사용해 커밋 후 처리한다.
- 실패 시 ERROR 로그를 남기고 관리자가 수동으로 재처리한다.

## 변경 파일

### 1. 이벤트 클래스 생성
경로: `backend/main/java/com/linkup/Petory/domain/user/event/UserSanctionAppliedEvent.java`

```java
package com.linkup.Petory.domain.user.event;

import com.linkup.Petory.domain.user.entity.UserStatus;
import java.time.LocalDateTime;

/**
 * 사용자 제재 적용 이벤트.
 * SUSPENDED 또는 BANNED 처리가 완료된 직후 발행된다.
 * 리스너는 @TransactionalEventListener(phase = AFTER_COMMIT) + REQUIRES_NEW 트랜잭션으로 처리한다.
 */
public record UserSanctionAppliedEvent(
        Long userId,
        UserStatus status,            // SUSPENDED 또는 BANNED
        LocalDateTime suspendedUntil  // SUSPENDED일 때만 유효, BANNED는 null
) {}
```

### 2. `UserSanctionService.java` 수정
경로: `backend/main/java/com/linkup/Petory/domain/user/service/UserSanctionService.java`

`ApplicationEventPublisher` 주입 및 `addSuspension()`/`addBan()` 끝에 이벤트 발행 추가.

**추가할 import:**
```java
import org.springframework.context.ApplicationEventPublisher;
import com.linkup.Petory.domain.user.event.UserSanctionAppliedEvent;
```

**필드 추가 (기존 final 필드들 아래):**
```java
private final ApplicationEventPublisher eventPublisher;
```

**`addSuspension()` 수정** — `usersRepository.save(user)` 직후 마지막 줄에 추가:
```java
eventPublisher.publishEvent(new UserSanctionAppliedEvent(user.getIdx(), UserStatus.SUSPENDED, endsAt));
log.info("제재 이벤트 발행(SUSPENDED): userId={}, until={}", user.getIdx(), endsAt);

return suspension;
```

**`addBan()` 수정** — `usersRepository.save(user)` 직후 마지막 줄에 추가:
```java
eventPublisher.publishEvent(new UserSanctionAppliedEvent(user.getIdx(), UserStatus.BANNED, null));
log.info("제재 이벤트 발행(BANNED): userId={}", user.getIdx());

return ban;
```

## 주의사항

- `@RequiredArgsConstructor`를 사용 중이므로 필드를 `private final`로 추가하면 Lombok이 생성자에 자동 포함한다. 별도 생성자 수정 불필요.
- `addWarning()`이 내부적으로 `addSuspension()`을 호출하는 경우(경고 3회 자동 이용제한)에도 이벤트가 발행된다. 의도된 동작이다.
- `applySanctionFromReport()`에서 `addSuspension()`을 호출하는 경우에도 이벤트가 발행된다.
- 이 step에서는 이벤트 리스너를 구현하지 않는다. 빈 리스너 없이 publishEvent만 해도 오류 없이 동작한다.

## AC (Acceptance Criteria)

```bash
# 컴파일 통과 확인
./gradlew compileJava

# 확인 포인트:
# - UserSanctionAppliedEvent.java 파일이 domain/user/event/ 에 존재
# - UserSanctionService에 ApplicationEventPublisher 필드가 있음
# - addSuspension / addBan 끝에 publishEvent 호출이 있음
```
