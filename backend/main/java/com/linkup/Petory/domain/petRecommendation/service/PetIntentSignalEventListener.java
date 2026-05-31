package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.client.PetIntentClient;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import com.linkup.Petory.domain.petRecommendation.event.CareRequestCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.CommunityPostCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.LocationSearchPerformedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetIntentSignalEventListener {

    private final PetIntentClient            petIntentClient;
    private final UserPetIntentSignalService signalService;

    // T1: 트랜잭션 커밋 완료 후 실행 — rollback 시 dangling signal 방지
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handle(CommunityPostCreatedEvent event) {
        analyze(event.getUserIdx(), "COMMUNITY", event.getPostId(), event.getText());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handle(CareRequestCreatedEvent event) {
        analyze(event.getUserIdx(), "CARE", event.getCareRequestId(), event.getText());
    }

    // LocationSearch는 트랜잭션 없이 발행되므로 @EventListener 유지
    @EventListener
    @Async
    public void handle(LocationSearchPerformedEvent event) {
        if (event.getUserIdx() == null) return;
        analyze(event.getUserIdx(), "LOCATION_SEARCH", null, event.getKeyword());
    }

    private void analyze(Long userIdx, String sourceType, Long sourceId, String text) {
        try {
            Optional<PetIntentAnalyzeResponse> result = petIntentClient.analyze(text, null);
            result.ifPresent(analysis ->
                    signalService.saveIfConfident(userIdx, sourceType, sourceId, analysis));
        } catch (Exception e) {
            log.warn("[SignalListener] 분석 실패 — 원 액션에 영향 없음. sourceType={} error={}",
                    sourceType, e.getMessage());
        }
    }
}
