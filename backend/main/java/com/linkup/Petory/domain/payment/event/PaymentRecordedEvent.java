package com.linkup.Petory.domain.payment.event;

import java.math.BigDecimal;

/**
 * 펫코인 에스크로 지급 완료 이벤트.
 * releaseToProvider 커밋 직후 발행되어, non-critical한 통계 집계를 결제(코인 지급) 트랜잭션에서 분리한다.
 * 리스너는 @TransactionalEventListener(phase = AFTER_COMMIT) + REQUIRES_NEW 트랜잭션으로 처리한다.
 */
public record PaymentRecordedEvent(BigDecimal amount) {}
