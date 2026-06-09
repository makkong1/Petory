package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.client.PetIntentClient;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import com.linkup.Petory.domain.petRecommendation.event.CareRequestCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.CommunityPostCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.LocationSearchPerformedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
/**
 * 도메인 이벤트를 받아 비동기로 NLP 분석 signal 저장을 트리거한다.
 *
 * <p>커뮤니티/케어는 커밋 이후 처리하고, 위치 검색은 자연어+dedup 필터를 통과한 경우만 처리한다.
 */
public class PetIntentSignalEventListener {

    private final PetIntentClient            petIntentClient;
    private final UserPetIntentSignalService signalService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final Duration LOC_DEDUP_TTL    = Duration.ofMinutes(10);
    private static final int      MIN_NL_LENGTH    = 7;
    private static final String   LOC_DEDUP_PREFIX = "nlp:loc-dedup:";

    public PetIntentSignalEventListener(
            PetIntentClient petIntentClient,
            UserPetIntentSignalService signalService,
            @Qualifier("customStringRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.petIntentClient = petIntentClient;
        this.signalService   = signalService;
        this.redisTemplate   = redisTemplate;
    }

    // T1: 트랜잭션 커밋 완료 후 실행 — rollback 시 dangling signal 방지
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("petIntentExecutor")
    public void handle(CommunityPostCreatedEvent event) {
        analyze(event.getUserIdx(), "COMMUNITY", event.getPostId(), event.getText(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("petIntentExecutor")
    public void handle(CareRequestCreatedEvent event) {
        analyze(event.getUserIdx(), "CARE", event.getCareRequestId(), event.getText(), event.getPetType());
    }

    // LocationSearch: 자연어 판단 + Redis TTL dedup 적용
    // 트랜잭션 없이 발행되므로 @EventListener 유지
    @EventListener
    @Async("petIntentExecutor")
    public void handle(LocationSearchPerformedEvent event) {
        if (event.getUserIdx() == null) return;

        String keyword = event.getKeyword();

        // 필터 1: 자연어 판단 — 짧거나 공백 없는 단순 카테고리 검색어는 분석 안 함
        // MVP 휴리스틱: length>=7 + 공백 포함. "강아지가귀를긁어요" 같은 붙여쓰기는 놓친다.
        // 목적은 과호출 방지이므로 허용 가능한 trade-off.
        if (!isNaturalLanguage(keyword)) {
            log.debug("[SignalListener] Location 검색 — 자연어 아님, skip. keyword={}", keyword);
            return;
        }

        // 필터 2: Redis TTL dedup — 10분 내 같은 user + keyword 분석됨
        // fail-closed: Redis 장애 시 Location NLP 분석 생략
        // (추천 signal은 부가 기능이라 Redis 장애 때 Python까지 호출하지 않는 것이 더 안전)
        String dedupKey = LOC_DEDUP_PREFIX + event.getUserIdx() + ":" + normalize(keyword);
        try {
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", LOC_DEDUP_TTL);
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("[SignalListener] Location 검색 dedup — 10분 내 동일 keyword, skip. userIdx={}",
                        event.getUserIdx());
                return;
            }
        } catch (Exception e) {
            log.warn("[SignalListener] Redis dedup 체크 실패 — Location NLP 분석 생략 (fail-closed). userIdx={} error={}",
                    event.getUserIdx(), e.getMessage());
            return;
        }

        analyze(event.getUserIdx(), "LOCATION_SEARCH", null, keyword, null);
    }

    private void analyze(Long userIdx, String sourceType, Long sourceId,
                         String text, String petType) {
        try {
            Optional<PetIntentAnalyzeResponse> result = petIntentClient.analyze(text, petType);
            result.ifPresent(analysis ->
                    signalService.saveIfConfident(userIdx, sourceType, sourceId, analysis));
        } catch (Exception e) {
            log.warn("[SignalListener] 분석 실패 — 원 액션에 영향 없음. sourceType={} error={}",
                    sourceType, e.getMessage());
        }
    }

    /** 공백 collapse + trim + lowercase */
    static String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /** 자연어 판단: 정규화 후 7자 이상 + 공백 포함 */
    static boolean isNaturalLanguage(String text) {
        if (text == null) return false;
        String n = normalize(text);
        return n.length() >= MIN_NL_LENGTH && n.contains(" ");
    }
}
