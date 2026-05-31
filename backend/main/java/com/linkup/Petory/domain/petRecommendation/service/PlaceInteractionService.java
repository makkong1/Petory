package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;
import com.linkup.Petory.domain.petRecommendation.repository.LocationInteractionCount;
import com.linkup.Petory.domain.petRecommendation.repository.PlaceInteractionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceInteractionService {

    private static final int POPULARITY_WINDOW_DAYS = 30;

    private final PlaceInteractionLogRepository logRepository;

    @Transactional
    public void record(long userIdx, Long locationIdx, String interactionType) {
        logRepository.save(PlaceInteractionLog.builder()
                .userIdx(userIdx)
                .locationIdx(locationIdx)
                .interactionType(interactionType)
                .build());
    }

    @Transactional(readOnly = true)
    public Map<Long, Double> getPopularityScores(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) return Map.of();
        LocalDateTime since = LocalDateTime.now().minusDays(POPULARITY_WINDOW_DAYS);
        List<LocationInteractionCount> rows = logRepository.countByLocationIdsSince(locationIds, since);
        return rows.stream().collect(Collectors.toMap(
                LocationInteractionCount::locationIdx,
                r -> Math.min(Math.log10(r.count() + 1.0) / Math.log10(1001), 1.0)
        ));
    }
}
