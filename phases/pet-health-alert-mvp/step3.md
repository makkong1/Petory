# Step 3 — PetHealthAlertNotificationHandler 생성

## 목적
Step 2에서 publish된 `SignalSavedEvent`를 받아, MEDICAL+HIGH 조건일 때만 `NotificationService.createNotification()`을 호출한다.
`@TransactionalEventListener(phase = AFTER_COMMIT)` 덕분에 signal DB 커밋이 완료된 이후에만 실행된다.

## 알림 페이로드 설계
| 필드 | 값 |
|------|-----|
| `type` | `NotificationType.PET_HEALTH_ALERT` |
| `title` | `"반려동물 건강 알림"` |
| `content` | `"위급할 수 있어요. 가까운 동물병원에 바로 문의하세요."` |
| `relatedType` | `"PET_INTENT_SIGNAL"` |
| `relatedId` | `signalId` |

프론트 `Navigation.js`는 `notification.type === 'PET_HEALTH_ALERT'`를 보고 라우팅한다.
`relatedType`은 현재 네비게이션 로직에서 사용하지 않지만, 데이터 추적용으로 남긴다.

---

## 신규 파일 — PetHealthAlertNotificationHandler.java

**경로**: `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetHealthAlertNotificationHandler.java`

```java
package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.notification.entity.NotificationType;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.petRecommendation.event.SignalSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetHealthAlertNotificationHandler {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("petIntentExecutor")
    public void handle(SignalSavedEvent event) {
        if (!"MEDICAL".equals(event.intentDomain()) || !"HIGH".equals(event.urgency())) {
            return;
        }
        try {
            notificationService.createNotification(
                    event.userIdx(),
                    NotificationType.PET_HEALTH_ALERT,
                    "반려동물 건강 알림",
                    "위급할 수 있어요. 가까운 동물병원에 바로 문의하세요.",
                    event.signalId(),
                    "PET_INTENT_SIGNAL"
            );
            log.info("[HealthAlert] 알림 발송 완료 userIdx={} signalId={}",
                    event.userIdx(), event.signalId());
        } catch (Exception e) {
            log.warn("[HealthAlert] 알림 발송 실패 — signal 저장에는 영향 없음. userIdx={} signalId={} error={}",
                    event.userIdx(), event.signalId(), e.getMessage());
        }
    }
}
```

## 주의사항
- `@Async("petIntentExecutor")`: `global/config/PetIntentAsyncConfig.java`에 이미 정의된 executor 사용.
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 조합 시 Spring은 별도 스레드에서 리스너를 실행하므로 원 트랜잭션 컨텍스트가 없다. `createNotification()` 내부의 `@Transactional`이 새 트랜잭션을 시작한다 — 정상 동작.
- 알림 발송 실패는 try/catch로 격리. signal 저장 성공은 이미 커밋된 상태이므로 롤백 없음.

## AC (Acceptance Criteria)
```bash
./gradlew compileJava
```
컴파일 오류 없이 통과해야 한다.
`PetHealthAlertNotificationHandler`가 Spring 컨텍스트에 등록되어야 한다 (`@Component`).
