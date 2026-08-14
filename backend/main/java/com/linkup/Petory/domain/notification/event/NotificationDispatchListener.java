package com.linkup.Petory.domain.notification.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.linkup.Petory.domain.notification.service.FcmService;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.notification.service.NotificationSseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 부가 채널(Redis 캐시, SSE, FCM) 발송 리스너. 도메인 트랜잭션 커밋 후 비동기로 실행되며, 채널별 실패를 서로
 * 격리한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchListener {

    private final NotificationService notificationService;
    private final NotificationSseService sseService;
    private final FcmService fcmService;

    @Async("notificationExecutor")
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
