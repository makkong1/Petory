package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.petRecommendation.client.PetIntentClient;
import com.linkup.Petory.domain.petRecommendation.dto.*;
import com.linkup.Petory.domain.petRecommendation.scoring.PetRecommendScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetRecommendationService {

    private final PetIntentClient             petIntentClient;
    private final LocationServiceRepository   locationServiceRepository;
    private final PetRecommendScoreCalculator scoreCalculator;
    private final PlaceInteractionService     interactionService;

    public PetRecommendResponse recommend(
            String text, double lat, double lng, int radius, String petType) {

        Optional<PetIntentAnalyzeResponse> analysisOpt = petIntentClient.analyze(text, petType);

        if (analysisOpt.isEmpty()) {
            return fallbackRecommend(text, lat, lng, radius);
        }

        PetIntentAnalyzeResponse analysis = analysisOpt.get();
        List<String> categories = analysis.getRecommendedCategories();

        if (categories == null || categories.isEmpty()) {
            return PetRecommendResponse.builder()
                    .requestText(text)
                    .analysis(analysis)
                    .message(analysis.getMessage())
                    .facilities(List.of())
                    .build();
        }

        String primaryCategory = categories.get(0);
        List<LocationService> nearby = locationServiceRepository
                .findByRadius(lat, lng, (double) radius, null, primaryCategory, "distance", 20);

        List<Long> locationIds = nearby.stream().map(LocationService::getIdx).toList();
        Map<Long, Double> popularityMap = interactionService.getPopularityScores(locationIds);

        List<String> intentTags = analysis.getIntentTags() != null ? analysis.getIntentTags() : List.of();
        List<PetRecommendFacilityDto> facilities = nearby.stream()
                .map(loc -> toDto(loc, lat, lng, intentTags, popularityMap))
                .map(dto -> scoreCalculator.calcScore(dto, radius, intentTags))
                .sorted(Comparator.comparingDouble(PetRecommendFacilityDto::getFinalScore).reversed())
                .limit(10)
                .toList();

        return PetRecommendResponse.builder()
                .requestText(text)
                .analysis(analysis)
                .message(analysis.getMessage())
                .facilities(facilities)
                .build();
    }

    PetRecommendResponse fallbackRecommend(String text, double lat, double lng, int radius) {
        log.warn("[PetRecommendationService] Python 서버 장애 — fallback 실행. text={}", text);
        String fallbackCategory = inferCategoryFromKeyword(text);
        List<LocationService> nearby = locationServiceRepository
                .findByRadius(lat, lng, (double) radius, null, fallbackCategory, "distance", 10);

        List<PetRecommendFacilityDto> facilities = nearby.stream()
                .map(loc -> toDto(loc, lat, lng, List.of(), Map.of()))
                .toList();

        return PetRecommendResponse.builder()
                .requestText(text)
                .analysis(null)
                .message("현재 반려생활 의도 분석 서버가 응답하지 않아 기본 검색 결과를 제공합니다.")
                .facilities(facilities)
                .build();
    }

    PetRecommendFacilityDto toDto(LocationService loc, double userLat, double userLng,
                                   List<String> intentTags, Map<Long, Double> popularityMap) {
        double distM = calcDistanceM(userLat, userLng,
                loc.getLatitude()  != null ? loc.getLatitude()  : 0,
                loc.getLongitude() != null ? loc.getLongitude() : 0);
        double popularity = popularityMap.getOrDefault(loc.getIdx(), 0.0);
        return PetRecommendFacilityDto.builder()
                .id(loc.getIdx())
                .name(loc.getName())
                .address(loc.getAddress())
                .distanceM(Math.round(distM * 10.0) / 10.0)
                .rating(loc.getRating()       != null ? loc.getRating()       : 0.0)
                .reviewCount(loc.getReviewCount() != null ? loc.getReviewCount() : 0)
                .finalScore(0.0)
                .matchReasons(List.of("nearby"))
                .locationTags(loc.getTagList())
                .popularityScore(popularity)
                .build();
    }

    private String inferCategoryFromKeyword(String text) {
        if (text == null || text.isBlank()) return null;
        if (text.contains("병원") || text.contains("약국") || text.contains("아파") || text.contains("긁")) return "동물병원";
        if (text.contains("미용") || text.contains("털") || text.contains("목욕")) return "미용";
        if (text.contains("사료") || text.contains("간식") || text.contains("용품") || text.contains("모래")) return "반려동물용품";
        if (text.contains("카페")) return "카페";
        if (text.contains("식당") || text.contains("맛집")) return "식당";
        if (text.contains("맡") || text.contains("유치원") || text.contains("위탁")) return "위탁관리";
        if (text.contains("호텔") || text.contains("펜션")) return "호텔";
        if (text.contains("산책") || text.contains("나들이") || text.contains("여행")) return "여행지";
        return null;
    }

    private double calcDistanceM(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
