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
