package com.linkup.Petory.domain.petRecommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.entity.UserPetIntentSignal;
import com.linkup.Petory.domain.petRecommendation.repository.UserPetIntentSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPetIntentSignalService {

    // Python confidence_threshold(0.45)보다 높게 설정하여 2-pass 품질 필터 구성.
    // Python 1차 필터: 0.45 미만 → UNKNOWN 반환, Spring 2차 필터: 0.60 미만 → 저장 거부.
    private static final double CONFIDENCE_THRESHOLD = 0.6;
    private static final int    SIGNAL_TTL_DAYS      = 7;
    private static final int    ACTIVE_SIGNAL_LIMIT  = 10;

    private final UserPetIntentSignalRepository signalRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveIfConfident(Long userIdx, String sourceType, Long sourceId,
                                PetIntentAnalyzeResponse analysis) {
        if (analysis == null || analysis.getConfidence() < CONFIDENCE_THRESHOLD) {
            log.debug("[Signal] confidence 미달 또는 분석 없음 — 저장 안 함. confidence={}",
                    analysis != null ? analysis.getConfidence() : "null");
            return;
        }
        // R3: 같은 도메인 유효 signal이 이미 있으면 중복 저장 방지
        if (signalRepository.existsByUserIdxAndIntentDomainAndExpiresAtAfter(
                userIdx, analysis.getIntentDomain(), LocalDateTime.now())) {
            log.debug("[Signal] 같은 도메인 유효 signal 존재 — 저장 스킵. domain={}", analysis.getIntentDomain());
            return;
        }
        try {
            String categoriesJson = objectMapper.writeValueAsString(analysis.getRecommendedCategories());
            String tagsJson       = objectMapper.writeValueAsString(analysis.getIntentTags());
            UserPetIntentSignal signal = UserPetIntentSignal.builder()
                    .userIdx(userIdx)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .intentDomain(analysis.getIntentDomain())
                    .intent(analysis.getIntent())
                    .recommendedCategories(categoriesJson)
                    .confidence(analysis.getConfidence())
                    .intentTags(tagsJson)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(SIGNAL_TTL_DAYS))
                    .build();
            signalRepository.save(signal);
            log.info("[Signal] 저장 완료 userIdx={} domain={} confidence={}",
                    userIdx, analysis.getIntentDomain(), analysis.getConfidence());
        } catch (Exception e) {
            log.warn("[Signal] JSON 직렬화 실패 — 저장 안 함", e);
        }
    }

    @Transactional(readOnly = true)
    public List<UserPetIntentSignalResponse> getActiveSignals(long userIdx) {
        List<UserPetIntentSignal> signals =
                signalRepository.findActiveByUser(userIdx, LocalDateTime.now(),
                        PageRequest.of(0, ACTIVE_SIGNAL_LIMIT));
        return signals.stream()
                .map(this::toResponse)
                .toList();
    }

    private UserPetIntentSignalResponse toResponse(UserPetIntentSignal s) {
        List<String> categories = parseJson(s.getRecommendedCategories());
        List<String> tags       = parseJson(s.getIntentTags());
        String cardMessage = buildCardMessage(s.getIntentDomain(), categories);
        String targetCategory = categories.isEmpty() ? null : categories.get(0);
        return UserPetIntentSignalResponse.builder()
                .intentDomain(s.getIntentDomain())
                .intent(s.getIntent())
                .recommendedCategories(categories)
                .confidence(s.getConfidence())
                .intentTags(tags)
                .cardMessage(cardMessage)
                .actionLabel(targetCategory != null ? "근처 " + targetCategory + " 보기" : "주변 서비스 보기")
                .targetTab("location")
                .targetCategory(targetCategory)
                .build();
    }

    private List<String> parseJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildCardMessage(String domain, List<String> categories) {
        return switch (domain != null ? domain : "") {
            case "MEDICAL"        -> "최근 건강 관련 고민이 있어 보여요.";
            case "GROOMING"       -> "반려동물 미용이 필요해 보여요.";
            case "CAFE_DINING"    -> "반려동물과 나들이 어떠세요?";
            case "LODGING_TRAVEL" -> "여행 계획 중이신가요?";
            case "SUPPLIES"       -> "반려동물 용품이 필요해 보여요.";
            default               -> "최근 입력을 바탕으로 추천합니다.";
        };
    }
}
