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
