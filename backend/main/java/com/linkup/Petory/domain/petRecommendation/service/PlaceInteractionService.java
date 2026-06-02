package com.linkup.Petory.domain.petRecommendation.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;
import com.linkup.Petory.domain.petRecommendation.repository.LocationInteractionCount;
import com.linkup.Petory.domain.petRecommendation.repository.PlaceInteractionLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * 장소 상호작용 로그를 저장하고 추천용 인기 점수를 계산한다.
 *
 * <p>점수는 최근 30일 상호작용 건수를 로그 스케일로 0~1 사이로 정규화해 사용한다.
 */
public class PlaceInteractionService {

    private static final int POPULARITY_WINDOW_DAYS = 30;

    private final PlaceInteractionLogRepository logRepository;

    @Transactional
    public void record(long userIdx, Long locationIdx, String interactionType) {
        // 상호작용 원본 이벤트를 그대로 저장해 이후 집계/모델 개선에 재사용한다.
        logRepository.save(PlaceInteractionLog.builder()
                .userIdx(userIdx)
                .locationIdx(locationIdx)
                .interactionType(interactionType)
                .build());
    }

    public Map<Long, Double> getPopularityScores(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime since = LocalDateTime.now().minusDays(POPULARITY_WINDOW_DAYS);
        List<LocationInteractionCount> rows = logRepository.countByLocationIdsSince(locationIds, since);
        return rows.stream().collect(Collectors.toMap(
                LocationInteractionCount::locationIdx,
                // count=0 -> 0.0, count=1000 근처 -> 1.0으로 포화되도록 제한
                r -> Math.min(Math.log10(r.count() + 1.0) / Math.log10(1001), 1.0)
        ));
    }
}
