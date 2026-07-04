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
