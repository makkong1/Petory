package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.event.CareRequestCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.CommunityPostCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.LocationSearchPerformedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1: 이벤트 핸들러 어노테이션 검증.
 * COMMUNITY/CARE → @TransactionalEventListener(AFTER_COMMIT) 으로 rollback 후 dangling signal 방지.
 * LOCATION_SEARCH → 트랜잭션 없이 발행되므로 @EventListener 유지.
 */
class PetIntentSignalEventListenerTest {

    @Test
    @DisplayName("CommunityPost 핸들러는 @TransactionalEventListener AFTER_COMMIT 을 사용한다 (T1)")
    void communityHandler_hasTransactionalEventListenerAfterCommit() throws Exception {
        Method method = PetIntentSignalEventListener.class
                .getDeclaredMethod("handle", CommunityPostCreatedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation)
                .as("@TransactionalEventListener 가 없음 — rollback 후 signal 중복 저장 위험")
                .isNotNull();
        assertThat(annotation.phase())
                .as("phase 가 AFTER_COMMIT 이어야 함")
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("CareRequest 핸들러는 @TransactionalEventListener AFTER_COMMIT 을 사용한다 (T1)")
    void careHandler_hasTransactionalEventListenerAfterCommit() throws Exception {
        Method method = PetIntentSignalEventListener.class
                .getDeclaredMethod("handle", CareRequestCreatedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("LocationSearch 핸들러는 @EventListener 를 사용한다 — 트랜잭션 없이 발행되므로 (T1)")
    void locationSearchHandler_usesEventListener() throws Exception {
        Method method = PetIntentSignalEventListener.class
                .getDeclaredMethod("handle", LocationSearchPerformedEvent.class);

        EventListener annotation = method.getAnnotation(EventListener.class);
        TransactionalEventListener txAnnotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation)
                .as("@EventListener 가 있어야 함 — 트랜잭션 없는 발행 처리")
                .isNotNull();
        assertThat(txAnnotation)
                .as("@TransactionalEventListener 가 있으면 트랜잭션 없는 발행 시 실행되지 않음")
                .isNull();
    }
}
