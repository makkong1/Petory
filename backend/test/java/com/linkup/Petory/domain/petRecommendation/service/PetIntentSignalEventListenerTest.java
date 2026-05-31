package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.client.PetIntentClient;
import com.linkup.Petory.domain.petRecommendation.event.CareRequestCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.CommunityPostCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.LocationSearchPerformedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T1: AFTER_COMMIT / @EventListener 어노테이션 검증.
 * petIntentExecutor qualifier 검증.
 * Step2: 자연어 판단, normalize, Redis dedup, fail-closed 검증.
 */
@ExtendWith(MockitoExtension.class)
class PetIntentSignalEventListenerTest {

    @Mock private PetIntentClient petIntentClient;
    @Mock private UserPetIntentSignalService signalService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private PetIntentSignalEventListener listener;

    // ===== T1: 어노테이션 검증 =====

    @Test
    @DisplayName("CommunityPost 핸들러는 @TransactionalEventListener AFTER_COMMIT 을 사용한다 (T1)")
    void communityHandler_hasTransactionalEventListenerAfterCommit() throws Exception {
        Method method = PetIntentSignalEventListener.class
                .getDeclaredMethod("handle", CommunityPostCreatedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).as("@TransactionalEventListener 없음").isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
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
    @DisplayName("LocationSearch 핸들러는 @EventListener 를 사용한다 (T1)")
    void locationSearchHandler_usesEventListener() throws Exception {
        Method method = PetIntentSignalEventListener.class
                .getDeclaredMethod("handle", LocationSearchPerformedEvent.class);

        assertThat(method.getAnnotation(EventListener.class)).isNotNull();
        assertThat(method.getAnnotation(TransactionalEventListener.class)).isNull();
    }

    @Test
    @DisplayName("세 핸들러 모두 @Async(\"petIntentExecutor\") 를 사용한다")
    void allHandlers_usesPetIntentExecutorQualifier() throws Exception {
        for (Class<?> eventType : new Class[]{
                CommunityPostCreatedEvent.class,
                CareRequestCreatedEvent.class,
                LocationSearchPerformedEvent.class}) {

            Method method = PetIntentSignalEventListener.class.getDeclaredMethod("handle", eventType);
            Async async = method.getAnnotation(Async.class);

            assertThat(async).as("@Async 없음: " + eventType.getSimpleName()).isNotNull();
            assertThat(async.value())
                    .as("petIntentExecutor qualifier 없음: " + eventType.getSimpleName())
                    .isEqualTo("petIntentExecutor");
        }
    }

    // ===== Step2: 자연어 판단 =====

    @Test
    @DisplayName("공백 없는 단순 카테고리 키워드는 자연어가 아니다")
    void isNaturalLanguage_noSpace_returnsFalse() {
        assertThat(PetIntentSignalEventListener.isNaturalLanguage("동물병원")).isFalse();
        assertThat(PetIntentSignalEventListener.isNaturalLanguage("미용")).isFalse();
        assertThat(PetIntentSignalEventListener.isNaturalLanguage("카페")).isFalse();
    }

    @Test
    @DisplayName("공백 있어도 7자 미만이면 자연어가 아니다")
    void isNaturalLanguage_shortWithSpace_returnsFalse() {
        assertThat(PetIntentSignalEventListener.isNaturalLanguage("귀 치료")).isFalse(); // 4자
        assertThat(PetIntentSignalEventListener.isNaturalLanguage("털 미용")).isFalse(); // 4자
    }

    @Test
    @DisplayName("7자 이상 + 공백 포함이면 자연어로 판단한다")
    void isNaturalLanguage_longWithSpace_returnsTrue() {
        assertThat(PetIntentSignalEventListener.isNaturalLanguage("강아지 귀 긁어요")).isTrue();
        assertThat(PetIntentSignalEventListener.isNaturalLanguage("고양이가 밥을 안 먹어요")).isTrue();
    }

    @Test
    @DisplayName("null 은 자연어가 아니다")
    void isNaturalLanguage_null_returnsFalse() {
        assertThat(PetIntentSignalEventListener.isNaturalLanguage(null)).isFalse();
    }

    // ===== Step2: normalize =====

    @Test
    @DisplayName("normalize — 중복 공백을 단일 공백으로 축소한다")
    void normalize_collapsesWhitespace() {
        assertThat(PetIntentSignalEventListener.normalize("강아지  귀  긁어요"))
                .isEqualTo("강아지 귀 긁어요");
    }

    @Test
    @DisplayName("normalize — 앞뒤 공백을 제거한다")
    void normalize_trimsBothEnds() {
        assertThat(PetIntentSignalEventListener.normalize("  동물병원  ")).isEqualTo("동물병원");
    }

    @Test
    @DisplayName("normalize — null 은 빈 문자열로 반환한다")
    void normalize_null_returnsEmpty() {
        assertThat(PetIntentSignalEventListener.normalize(null)).isEqualTo("");
    }

    // ===== Step2: LocationSearch 이벤트 처리 =====

    @Test
    @DisplayName("자연어가 아닌 keyword 는 Python 호출 없이 skip 한다")
    void locationSearch_nonNaturalLanguage_skipsAnalysis() {
        LocationSearchPerformedEvent event =
                new LocationSearchPerformedEvent(this, 1L, "동물병원");

        listener.handle(event);

        verifyNoInteractions(redisTemplate, petIntentClient);
    }

    @Test
    @DisplayName("Redis dedup 키가 이미 있으면 Python 호출 없이 skip 한다")
    void locationSearch_dedupKeyExists_skipsAnalysis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        LocationSearchPerformedEvent event =
                new LocationSearchPerformedEvent(this, 1L, "강아지 귀 긁어요");

        listener.handle(event);

        verify(valueOps).setIfAbsent(anyString(), eq("1"), eq(Duration.ofMinutes(10)));
        verifyNoInteractions(petIntentClient);
    }

    @Test
    @DisplayName("자연어 + 새 keyword 이면 Python 분석을 호출한다")
    void locationSearch_newNaturalLanguage_callsAnalysis() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(petIntentClient.analyze(anyString(), isNull())).thenReturn(Optional.empty());

        LocationSearchPerformedEvent event =
                new LocationSearchPerformedEvent(this, 1L, "강아지 귀 긁어요");

        listener.handle(event);

        verify(petIntentClient).analyze("강아지 귀 긁어요", null);
    }

    @Test
    @DisplayName("Redis 장애 시 Python 호출 없이 skip 한다 (fail-closed)")
    void locationSearch_redisDown_skipsAnalysis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        LocationSearchPerformedEvent event =
                new LocationSearchPerformedEvent(this, 1L, "강아지 귀 긁어요");

        listener.handle(event);

        verifyNoInteractions(petIntentClient);
    }

    @Test
    @DisplayName("userIdx 가 null 이면 모든 처리를 skip 한다")
    void locationSearch_nullUserIdx_skipsAll() {
        LocationSearchPerformedEvent event =
                new LocationSearchPerformedEvent(this, null, "강아지 귀 긁어요");

        listener.handle(event);

        verifyNoInteractions(redisTemplate, petIntentClient);
    }
}
