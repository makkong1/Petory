package com.linkup.Petory.domain.statistics.event;

import com.linkup.Petory.domain.payment.event.PaymentRecordedEvent;
import com.linkup.Petory.domain.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 에스크로 지급 완료 후 결제액을 일별 통계에 집계한다. AFTER_COMMIT + REQUIRES_NEW: 통계
 * 집계(non-critical)가 실패해도 이미 커밋된 코인 지급은 롤백되지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecordedStatisticsListener {

    private final StatisticsService statisticsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentRecorded(PaymentRecordedEvent event) {
        try {
            statisticsService.recordPayment(event.amount());
        } catch (Exception e) {
            log.error("결제 통계 집계 실패 (코인 지급은 정상 완료됨): amount={}, error={}",
                    event.amount(), e.getMessage(), e);
        }
    }
}
