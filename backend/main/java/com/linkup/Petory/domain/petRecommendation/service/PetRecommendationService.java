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

    private final PetIntentClient petIntentClient;
    private final LocationServiceRepository locationServiceRepository;
    private final PetRecommendScoreCalculator scoreCalculator;

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

        List<String> intentTags = analysis.getIntentTags() != null ? analysis.getIntentTags() : List.of();
        List<PetRecommendFacilityDto> facilities = nearby.stream()
                .limit(10)
                .map(loc -> toDto(loc, lat, lng, intentTags))
                .map(dto -> scoreCalculator.calcScore(dto, radius, intentTags))
                .sorted(Comparator.comparingDouble(PetRecommendFacilityDto::getFinalScore).reversed())
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
                .map(loc -> toDto(loc, lat, lng, List.of()))
                .toList();

        return PetRecommendResponse.builder()
                .requestText(text)
                .analysis(null)
                .message("현재 반려생활 의도 분석 서버가 응답하지 않아 기본 검색 결과를 제공합니다.")
                .facilities(facilities)
                .build();
    }

    PetRecommendFacilityDto toDto(
            LocationService loc, double userLat, double userLng, List<String> intentTags) {
        double distM = calcDistanceM(userLat, userLng,
                loc.getLatitude() != null ? loc.getLatitude() : 0,
                loc.getLongitude() != null ? loc.getLongitude() : 0);
        return PetRecommendFacilityDto.builder()
                .id(loc.getIdx())
                .name(loc.getName())
                .address(loc.getAddress())
                .distanceM(Math.round(distM * 10.0) / 10.0)
                .rating(loc.getRating() != null ? loc.getRating() : 0.0)
                .reviewCount(loc.getReviewCount() != null ? loc.getReviewCount() : 0)
                .finalScore(0.0)
                .matchReasons(List.of("nearby"))
                .build();
    }

    private String inferCategoryFromKeyword(String text) {
        if (text.contains("병원") || text.contains("약국") || text.contains("아파") || text.contains("긁")) return "동물병원";
        if (text.contains("미용") || text.contains("털") || text.contains("목욕")) return "미용";
        if (text.contains("카페")) return "카페";
        if (text.contains("호텔") || text.contains("펜션")) return "호텔";
        return "동물병원";
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
