# Step 2 — 알림 발송(Redis/SSE/FCM)을 AFTER_COMMIT 비동기 리스너로 분리

## 배경

`NotificationService.createNotification`(line 65~94)이 `@Transactional` 안에서:

```
1. DB 저장 (notificationRepository.save)
2. saveToRedis(userId, dto)          ← Redis I/O
3. sseService.sendNotification(...)  ← SSE 전송
4. fcmService.sendToUser(...)        ← FCM HTTP 호출 (@Async 아님, 동기)
```

문제 2가지 (리뷰 문서 [B2] + [W-NOTI]):

1. **커넥션 점유**: FCM HTTP 응답이 느리면 그 시간만큼 DB 커넥션과 트랜잭션을 점유. 알림은 모든 도메인(댓글, 케어 매칭, 모임 등)이 호출하는 공통 경로 → 커넥션 풀(20개) 고갈 진원지.
2. **장애 역결합**: 클래스 전체에 try-catch가 0건이라, Redis 다운/SSE 예외 발생 시 `createNotification`이 실패하고 **호출한 도메인 작업(댓글 작성 등)의 트랜잭션까지 롤백**된다. 부가 기능이 핵심 기능의 가용성을 좌우.
3. (부수 효과) 현재 구조는 트랜잭션이 롤백돼도 푸시가 이미 나갈 수 있다 — AFTER_COMMIT 분리로 함께 해소.

**프로젝트에 이미 있는 패턴을 그대로 따른다**: `domain/care/event/UserSanctionCareEventListener` — `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` + `@Transactional(propagation = Propagation.REQUIRES_NEW)` + 내부 try-catch. 이벤트 규약은 `UserSanctionAppliedEvent` 클래스 주석에 명시돼 있음.

## 수정 대상 파일

- (신규) `backend/main/java/com/linkup/Petory/domain/notification/event/NotificationCreatedEvent.java`
- (신규) `backend/main/java/com/linkup/Petory/domain/notification/event/NotificationDispatchListener.java`
- `backend/main/java/com/linkup/Petory/domain/notification/service/NotificationService.java`

## 수정 내용

### 1. NotificationCreatedEvent (신규, record)

```java
package com.linkup.Petory.domain.notification.event;

import com.linkup.Petory.domain.notification.dto.NotificationDTO;

/**
 * 알림 DB 저장 커밋 후 부가 채널(Redis 캐시, SSE, FCM) 발송을 트리거하는 이벤트.
 * 리스너는 @TransactionalEventListener(AFTER_COMMIT) + @Async로 처리하며,
 * 채널별 실패는 서로 격리한다 (알림 채널 장애가 도메인 트랜잭션에 영향 없음).
 */
public record NotificationCreatedEvent(
        Long userId,
        NotificationDTO notification,
        String title,
        String content) {
}
```

### 2. NotificationDispatchListener (신규)

```java
package com.linkup.Petory.domain.notification.event;

// imports...

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchListener {

    private final NotificationService notificationService;
    private final NotificationSseService sseService;
    private final FcmService fcmService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        // 채널별 예외 격리 — 한 채널 장애가 다른 채널 발송을 막지 않는다
        try {
            notificationService.cacheToRedis(event.userId(), event.notification());
        } catch (Exception e) {
            log.error("알림 Redis 캐시 실패 (DB 알림은 정상 저장됨): userId={}", event.userId(), e);
        }
        try {
            sseService.sendNotification(event.userId(), event.notification());
        } catch (Exception e) {
            log.error("알림 SSE 전송 실패: userId={}", event.userId(), e);
        }
        try {
            fcmService.sendToUser(event.userId(), event.title(), event.content());
        } catch (Exception e) {
            log.error("알림 FCM 발송 실패: userId={}", event.userId(), e);
        }
    }
}
```

### 3. NotificationService 수정

- `ApplicationEventPublisher` 주입 추가.
- `createNotification`에서 `saveToRedis(...)` / `sseService.sendNotification(...)` / `fcmService.sendToUser(...)` 3줄 제거 → `eventPublisher.publishEvent(new NotificationCreatedEvent(userId, dto, title, content));` 로 대체.
- `private void saveToRedis(...)` → **`public void cacheToRedis(...)`로 이름 변경 + 가시성 상향** (리스너가 호출). 내부 로직은 그대로.
  - **주의**: `saveToRedis`를 호출하는 다른 내부 메서드가 있는지 먼저 확인(`grep -n "saveToRedis" NotificationService.java`)하고, 있으면 호출부도 새 이름으로 일괄 변경.
- `createNotification`의 반환값(`NotificationDTO`)은 그대로 유지 — 호출자 인터페이스 불변.

### 리스너 vs 서비스 순환 주의

`NotificationDispatchListener → NotificationService`는 단방향 의존이다. `NotificationService`에 리스너를 주입하지 말 것 (순환). SSE/FCM 필드는 `NotificationService`에서 더 이상 사용하지 않으면 **이번 변경으로 불필요해진 것이므로 제거**한다 (CLAUDE.md: 내 변경 때문에 불필요해진 import·필드만 제거).

## 동작 유의사항 (구현 에이전트 필독)

- `@TransactionalEventListener`는 **트랜잭션이 없으면 기본적으로 발화하지 않는다**(`fallbackExecution=false`). `createNotification`은 `@Transactional`이므로 항상 트랜잭션이 있다 — 안전. 단, 확인 차원에서 `createNotification`을 트랜잭션 없이 직접 호출하는 경로가 없는지 grep으로 검증할 것.
- `@Async`는 `PetoryApplication`의 `@EnableAsync`로 이미 활성화돼 있다. executor는 기본 `applicationTaskExecutor`를 사용한다 (전용 executor 신설은 scope 밖 — 리뷰 문서 §9 Info 항목으로 별도 관리).
- Redis 캐시가 커밋 후 비동기로 이동하므로, 알림 생성 직후 목록 조회 시 Redis에 아직 없을 수 있다 → 기존 코드가 Redis/DB 병합 조회(중복 제거)를 하므로 DB에서 조회됨. 이 병합 로직이 실제로 있는지 `getNotifications` 계열 메서드에서 확인하고, 없다면 step을 `blocked` 처리하고 사유를 기록할 것.

## 가드레일

- `markAsRead`/`markAllAsRead`/삭제 등 다른 메서드의 Redis 갱신 로직은 **건드리지 않는다**. 이 step은 `createNotification` 경로만 분리한다.
- 기존 제재 이벤트 리스너 규약(AFTER_COMMIT, 예외 삼킴+로그)과 스타일을 맞춘다.

## AC (Acceptance Criteria)

```bash
./gradlew compileJava
./gradlew test --tests '*Notification*'   # MySQL+Redis 필요
```

추가 검증(테스트 코드로 작성 권장): `createNotification` 호출 트랜잭션이 롤백되면 SSE/FCM이 발송되지 않아야 한다 (`@RecordApplicationEvents` 또는 리스너 mock으로 검증).
