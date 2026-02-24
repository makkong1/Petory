package com.linkup.Petory.domain.location.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.linkup.Petory.domain.location.dto.LocationServiceDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주변 서비스 추천 에이전트 (Ollama 연동).
 * 검색 결과를 LLM에 넘겨 상위 10개 재순위화 + 각 1줄 추천 이유 추가.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationRecommendAgentService {

    private static final int MAX_CANDIDATES = 30;
    private static final int MAX_RECOMMEND = 10;

    @Value("${app.ai.recommend.timeout-seconds:30}")
    private int timeoutSeconds;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        너는 반려동물과 함께 가기 좋은 장소를 추천하는 AI다.
        다음 장소 목록에서 상위 10개를 추천 순서로 정렬하고, 각 장소별 1줄 추천 이유를 한국어로 달아라.
        고려 기준(중요도 순): 1) 거리(가까울수록) 2) 반려동물 친화 3) 평점 4) 리뷰 수 5) 카테고리.
        JSON만 한 줄로 출력해라. 형식: {"recommendations":[{"idx":숫자,"reason":"이유"},...]}
        """;

    /**
     * 검색 결과에 AI 추천 순서·이유를 붙여 반환. 실패 시 원본 그대로 반환.
     */
    public List<LocationServiceDTO> enrichWithRecommendations(
            List<LocationServiceDTO> services,
            String category) {
        if (services == null || services.isEmpty()) {
            return services;
        }

        List<LocationServiceDTO> candidates = services.stream().limit(MAX_CANDIDATES).toList();
        if (candidates.isEmpty()) {
            return services;
        }

        long startMs = System.currentTimeMillis();
        log.info("[AI추천] 시작 후보={}개", candidates.size());

        try {
            String userMessage = buildPlaceList(candidates);
            String response = CompletableFuture
                    .supplyAsync(() -> chatModel.call(new Prompt(SYSTEM_PROMPT + "\n\n" + userMessage))
                            .getResult()
                            .getOutput()
                            .getText())
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();

            if (response == null || response.isBlank()) {
                log.warn("[AI추천] 끝 (응답 비어있음) 소요={}ms", System.currentTimeMillis() - startMs);
                return services.stream().limit(MAX_RECOMMEND).toList();
            }

            List<LocationServiceDTO> result = parseAndApply(candidates, response);
            log.info("[AI추천] 끝 성공 소요={}ms 결과={}개", System.currentTimeMillis() - startMs, result.size());
            return result;
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                log.warn("[AI추천] 끝 타임아웃 소요={}ms (제한={}초)", System.currentTimeMillis() - startMs, timeoutSeconds);
            } else {
                log.error("[AI추천] 끝 실패 소요={}ms err={}", System.currentTimeMillis() - startMs, cause != null ? cause.getMessage() : e.getMessage(), e);
            }
            return services.stream().limit(MAX_RECOMMEND).toList();
        } catch (Exception e) {
            log.error("[AI추천] 끝 실패 소요={}ms err={}", System.currentTimeMillis() - startMs, e.getMessage(), e);
            return services.stream().limit(MAX_RECOMMEND).toList();
        }
    }

    private String buildPlaceList(List<LocationServiceDTO> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("[장소 목록]\n");
        for (LocationServiceDTO dto : candidates) {
            sb.append("- idx:").append(dto.getIdx())
                    .append(" name:").append(dto.getName() != null ? dto.getName() : "")
                    .append(" distance:").append(dto.getDistance() != null ? String.format("%.0fm", dto.getDistance()) : "?")
                    .append(" rating:").append(dto.getRating() != null ? dto.getRating() : "?")
                    .append(" petFriendly:").append(Boolean.TRUE.equals(dto.getPetFriendly()) ? "Y" : "N")
                    .append(" category:").append(dto.getCategory() != null ? dto.getCategory() : "")
                    .append(" reviewCount:").append(dto.getReviewCount() != null ? dto.getReviewCount() : 0)
                    .append("\n");
        }
        sb.append("\n위 목록에서 상위 10개를 JSON으로 출력해라.");
        return sb.toString();
    }

    private List<LocationServiceDTO> parseAndApply(List<LocationServiceDTO> candidates, String response) {
        Map<Long, LocationServiceDTO> byIdx = candidates.stream()
                .collect(Collectors.toMap(LocationServiceDTO::getIdx, dto -> dto, (a, b) -> a, LinkedHashMap::new));

        try {
            String json = response.trim();
            if (json.contains("```")) {
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}') + 1;
                if (start >= 0 && end > start) {
                    json = json.substring(start, end);
                }
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode recs = root.get("recommendations");
            if (recs == null || !recs.isArray()) {
                return candidates.stream().limit(MAX_RECOMMEND).toList();
            }

            List<LocationServiceDTO> result = new ArrayList<>();
            for (JsonNode item : recs) {
                JsonNode idxNode = item.get("idx");
                JsonNode reasonNode = item.get("reason");
                if (idxNode == null) continue;

                long idx = idxNode.asLong();
                LocationServiceDTO dto = byIdx.get(idx);
                if (dto != null) {
                    LocationServiceDTO copy = cloneDto(dto);
                    if (reasonNode != null && reasonNode.isTextual()) {
                        copy.setRecommendationReason(reasonNode.asText().trim());
                    }
                    result.add(copy);
                }
                if (result.size() >= MAX_RECOMMEND) break;
            }

            if (result.isEmpty()) {
                return candidates.stream().limit(MAX_RECOMMEND).toList();
            }
            return result;
        } catch (Exception e) {
            log.warn("[AI추천] JSON 파싱 실패: {}", e.getMessage());
            return candidates.stream().limit(MAX_RECOMMEND).toList();
        }
    }

    private LocationServiceDTO cloneDto(LocationServiceDTO src) {
        LocationServiceDTO dto = new LocationServiceDTO();
        dto.setIdx(src.getIdx());
        dto.setExternalId(src.getExternalId());
        dto.setName(src.getName());
        dto.setCategory(src.getCategory());
        dto.setCategory1(src.getCategory1());
        dto.setCategory2(src.getCategory2());
        dto.setCategory3(src.getCategory3());
        dto.setAddress(src.getAddress());
        dto.setLatitude(src.getLatitude());
        dto.setLongitude(src.getLongitude());
        dto.setRating(src.getRating());
        dto.setPhone(src.getPhone());
        dto.setWebsite(src.getWebsite());
        dto.setPlaceUrl(src.getPlaceUrl());
        dto.setDescription(src.getDescription());
        dto.setPetFriendly(src.getPetFriendly());
        dto.setPetPolicy(src.getPetPolicy());
        dto.setSido(src.getSido());
        dto.setSigungu(src.getSigungu());
        dto.setEupmyeondong(src.getEupmyeondong());
        dto.setRoadName(src.getRoadName());
        dto.setZipCode(src.getZipCode());
        dto.setClosedDay(src.getClosedDay());
        dto.setOperatingHours(src.getOperatingHours());
        dto.setParkingAvailable(src.getParkingAvailable());
        dto.setPriceInfo(src.getPriceInfo());
        dto.setIsPetOnly(src.getIsPetOnly());
        dto.setPetSize(src.getPetSize());
        dto.setPetRestrictions(src.getPetRestrictions());
        dto.setPetExtraFee(src.getPetExtraFee());
        dto.setIndoor(src.getIndoor());
        dto.setOutdoor(src.getOutdoor());
        dto.setReviewCount(src.getReviewCount());
        dto.setReviews(src.getReviews());
        dto.setDistance(src.getDistance());
        dto.setLastUpdated(src.getLastUpdated());
        dto.setDataSource(src.getDataSource());
        dto.setIsDeleted(src.getIsDeleted());
        dto.setDeletedAt(src.getDeletedAt());
        return dto;
    }
}
