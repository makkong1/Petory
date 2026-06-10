package com.linkup.Petory.domain.petRecommendation.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.entity.UserPetIntentSignal;
import com.linkup.Petory.domain.petRecommendation.event.SignalSavedEvent;
import com.linkup.Petory.domain.petRecommendation.repository.UserPetIntentSignalRepository;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NLP 분석 결과를 {@link UserPetIntentSignal} 엔티티로 저장·조회하고, 프론트 추천 카드용
 * {@link UserPetIntentSignalResponse}로 변환하는 서비스.
 *
 * <p>
 * 원문 텍스트(게시글·케어·검색어)는 DB에 저장하지 않으며, intent·카테고리·confidence 등 요약 메타만
 * {@code user_pet_intent_signal} 테이블에 남긴다.
 *
 * <h3>호출 경로</h3>
 * <ul>
 * <li>저장: {@link PetIntentSignalEventListener} — 커뮤니티/케어/위치검색 이벤트 후
 * {@link com.linkup.Petory.domain.petRecommendation.client.PetIntentClient#analyze}
 * 결과를 넘김</li>
 * <li>조회: {@link com.linkup.Petory.domain.petRecommendation.controller.PetRecommendationController}
 *       {@code GET /api/pet-recommend/signals}</li>
 * </ul>
 *
 * <h3>저장 정책 ({@link #saveIfConfident})</h3>
 * <ol>
 * <li>confidence ≥ {@value #CONFIDENCE_THRESHOLD} (Python 1차 0.45보다 높은 Spring
 * 2차 필터)</li>
 * <li>동일 {@code (userIdx, intentDomain)} 유효 signal 없음 (R3, 만료 전 중복 방지)</li>
 * <li>TTL {@value #SIGNAL_TTL_DAYS}일 — {@code expiresAt} 기준</li>
 * </ol>
 *
 * <h3>조회 정책 ({@link #getActiveSignals})</h3>
 * 만료되지 않은 signal을 {@code createdAt} 내림차순으로 최대 {@value #ACTIVE_SIGNAL_LIMIT}건
 * (R2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPetIntentSignalService {

    private static final Map<String, Double> DOMAIN_THRESHOLDS = Map.of(
        "MEDICAL",          0.65,  // urgency=HIGH 시 thresholdFor()에서 0.55로 완화
        "FOOD_SNACK",       0.45,  // Python 1차 필터와 동일 — 오탐 비용 낮음
        "SUPPLIES",         0.45,
        "WALK_OUTING",      0.45,
        "CAFE_DINING",      0.45
    );
    private static final double DEFAULT_THRESHOLD = 0.60;
    /**
     * 활성 signal 조회 상한 (R2). {@link PageRequest#of(int, int)} 와 함께 사용
     */
    private static final int ACTIVE_SIGNAL_LIMIT = 10;

    private final UserPetIntentSignalRepository signalRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * NLP 분석 결과가 충분히 확실할 때만 DB에 signal을 insert 한다.
     *
     * <p>
     * 호출은 보통 비동기 이벤트 리스너에서 이루어지며, 저장 실패·스킵은 원 트랜잭션(게시/케어 등)에 영향을 주지 않는다.
     *
     * @param userIdx signal 소유 사용자 PK
     * @param sourceType 이벤트 출처 — {@code COMMUNITY} | {@code CARE} |
     * {@code LOCATION_SEARCH}
     * @param sourceId 출처 엔티티 PK (위치 검색은 null 가능)
     * @param analysis
     * {@link com.linkup.Petory.domain.petRecommendation.client.PetIntentClient}
     * 응답. null이면 무시
     */
    @Transactional
    public void saveIfConfident(Long userIdx, String sourceType, Long sourceId,
            PetIntentAnalyzeResponse analysis) {
        if (analysis == null
                || analysis.getConfidence() < thresholdFor(analysis.getIntentDomain(), analysis.getUrgency())) {
            log.debug("[Signal] 저장 스킵 — confidence 미달 또는 분석 없음. userIdx={} domain={} urgency={} confidence={} threshold={}",
                    userIdx,
                    analysis != null ? analysis.getIntentDomain() : "null",
                    analysis != null ? analysis.getUrgency() : "null",
                    analysis != null ? analysis.getConfidence() : "null",
                    analysis != null ? thresholdFor(analysis.getIntentDomain(), analysis.getUrgency()) : "null");
            return;
        }

        // R3: 같은 도메인·유효기간 내 signal이 있으면 insert 하지 않음
        if (signalRepository.existsByUserIdxAndIntentDomainAndExpiresAtAfter(
                userIdx, analysis.getIntentDomain(), LocalDateTime.now())) {
            log.debug("[Signal] 저장 스킵 — 동일 도메인 유효 signal 존재. userIdx={} domain={}",
                    userIdx, analysis.getIntentDomain());
            return;
        }

        final String categoriesJson;
        final String tagsJson;
        try {
            categoriesJson = objectMapper.writeValueAsString(analysis.getRecommendedCategories());
            tagsJson = objectMapper.writeValueAsString(analysis.getIntentTags());
        } catch (JsonProcessingException e) {
            log.warn("[Signal] 저장 스킵 — JSON 직렬화 실패. userIdx={} domain={}",
                    userIdx, analysis.getIntentDomain(), e);
            return;
        }

        try {
            UserPetIntentSignal signal = UserPetIntentSignal.builder()
                    .userIdx(userIdx)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .intentDomain(analysis.getIntentDomain())
                    .intent(analysis.getIntent())
                    .recommendedCategories(categoriesJson)
                    .confidence(analysis.getConfidence())
                    .urgency(analysis.getUrgency())
                    .intentTags(tagsJson)
                    .expiresAt(LocalDateTime.now().plusDays(ttlDaysFor(analysis.getIntentDomain(), analysis.getUrgency())))
                    .build();
            UserPetIntentSignal saved = signalRepository.save(signal);
            log.info("[Signal] 저장 완료 userIdx={} sourceType={} sourceId={} domain={} urgency={} confidence={} ttlDays={}",
                    userIdx, sourceType, sourceId, analysis.getIntentDomain(),
                    analysis.getUrgency(), analysis.getConfidence(),
                    ttlDaysFor(analysis.getIntentDomain(), analysis.getUrgency()));
            eventPublisher.publishEvent(new SignalSavedEvent(
                    userIdx,
                    saved.getId(),
                    analysis.getIntentDomain(),
                    analysis.getUrgency()
            ));
        } catch (Exception e) {
            log.warn("[Signal] 저장 실패 — DB 오류(스키마·연결 등). userIdx={} domain={}",
                    userIdx, analysis.getIntentDomain(), e);
        }
    }

    /**
     * 로그인 사용자의 만료되지 않은 signal 목록을 추천 카드 DTO로 반환한다.
     *
     * @param userIdx 인증 사용자 PK ({@code AuthenticatedUserIdResolver})
     * @return 최대 {@value #ACTIVE_SIGNAL_LIMIT}건. 없으면 빈 리스트
     */
    @Transactional(readOnly = true)
    public List<UserPetIntentSignalResponse> getActiveSignals(long userIdx) {
        List<UserPetIntentSignal> signals
                = signalRepository.findActiveByUser(userIdx, LocalDateTime.now(),
                        PageRequest.of(0, ACTIVE_SIGNAL_LIMIT));

        log.debug("[Signal] 활성 signal 조회 userIdx={} count={}", userIdx, signals.size());

        return signals.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * DB JSON 컬럼 → API용 DTO (카드 문구·액션 라벨 포함)
     */
    private UserPetIntentSignalResponse toResponse(UserPetIntentSignal s) {
        List<String> categories = parseJson(s.getRecommendedCategories());
        List<String> tags = parseJson(s.getIntentTags());
        String cardMessage = buildCardMessage(s.getIntentDomain(), s.getUrgency(), categories);
        String targetCategory = categories.isEmpty() ? null : categories.get(0);
        return UserPetIntentSignalResponse.builder()
                .intentDomain(s.getIntentDomain())
                .intent(s.getIntent())
                .recommendedCategories(categories)
                .confidence(s.getConfidence())
                .urgency(s.getUrgency())
                .intentTags(tags)
                .cardMessage(cardMessage)
                .actionLabel(targetCategory != null ? "근처 " + targetCategory + " 보기" : "주변 서비스 보기")
                .targetTab("location")
                .targetCategory(targetCategory)
                .build();
    }

    /**
     * 엔티티 JSON 문자열 → 리스트. 파싱 실패 시 빈 리스트 (카드 노출은 계속)
     */
    private List<String> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.debug("[Signal] JSON 파싱 실패 — 빈 리스트 반환. snippet={}",
                    json.length() > 80 ? json.substring(0, 80) + "…" : json);
            return List.of();
        }
    }

    private int ttlDaysFor(String domain, String urgency) {
        if ("MEDICAL".equals(domain)) {
            return "HIGH".equals(urgency) ? 1 : 3;
        }
        if ("LODGING_TRAVEL".equals(domain)) {
            return 14;
        }
        return 7;
    }

    private double thresholdFor(String domain, String urgency) {
        // MEDICAL+HIGH: 위급 signal 누락 비용 > 오탐 비용 → threshold 완화
        if ("MEDICAL".equals(domain) && "HIGH".equals(urgency)) {
            return 0.55;
        }
        return DOMAIN_THRESHOLDS.getOrDefault(domain, DEFAULT_THRESHOLD);
    }

    /**
     * intentDomain + urgency 기반 카드 본문.
     * MEDICAL+HIGH는 위급 문구, 나머지는 기존 도메인별 문구.
     * Phase 1에서 constraints 파라미터 추가 예정.
     */
    private String buildCardMessage(String domain, String urgency,
            @SuppressWarnings("unused") List<String> categories) {
        boolean isHigh = "HIGH".equals(urgency);
        return switch (domain != null ? domain : "") {
            case "MEDICAL"         -> isHigh
                    ? "위급할 수 있어요. 가까운 동물병원에 바로 문의하세요."
                    : "최근 건강 관련 고민이 있어 보여요.";
            case "GROOMING"        -> "반려동물 미용이 필요해 보여요.";
            case "CAFE_DINING"     -> "반려동물과 나들이 어떠세요?";
            case "LODGING_TRAVEL"  -> "여행 계획 중이신가요?";
            case "SUPPLIES"        -> "반려동물 용품이 필요해 보여요.";
            case "FOOD_SNACK"      -> "반려동물 먹거리가 필요해 보여요.";
            case "WALK_OUTING"     -> "반려동물과 산책하기 좋은 곳을 찾아드릴게요.";
            case "DAYCARE_BOARDING"-> "반려동물 돌봄 서비스가 필요해 보여요.";
            case "CULTURE_SPACE"   -> "반려동물과 함께하는 문화 공간을 찾아보세요.";
            default                -> "최근 입력을 바탕으로 추천합니다.";
        };
    }
}
