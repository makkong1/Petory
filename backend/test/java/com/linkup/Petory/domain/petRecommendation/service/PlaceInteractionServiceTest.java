package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;
import com.linkup.Petory.domain.petRecommendation.repository.LocationInteractionCount;
import com.linkup.Petory.domain.petRecommendation.repository.PlaceInteractionLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceInteractionServiceTest {

    @InjectMocks
    private PlaceInteractionService interactionService;

    @Mock
    private PlaceInteractionLogRepository logRepository;

    // ===== record =====

    @Test
    @DisplayName("record 는 PlaceInteractionLog 를 저장한다")
    void record_savesLog() {
        interactionService.record(1L, 42L, "VIEW");

        ArgumentCaptor<PlaceInteractionLog> captor = ArgumentCaptor.forClass(PlaceInteractionLog.class);
        verify(logRepository).save(captor.capture());

        PlaceInteractionLog log = captor.getValue();
        assertThat(log.getUserIdx()).isEqualTo(1L);
        assertThat(log.getLocationIdx()).isEqualTo(42L);
        assertThat(log.getInteractionType()).isEqualTo("VIEW");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    // ===== getPopularityScores =====

    @Test
    @DisplayName("locationIds 가 빈 리스트면 빈 맵을 반환한다")
    void getPopularityScores_emptyList_returnsEmptyMap() {
        Map<Long, Double> result = interactionService.getPopularityScores(List.of());

        assertThat(result).isEmpty();
        verify(logRepository, never()).countByLocationIdsSince(any(), any());
    }

    @Test
    @DisplayName("locationIds 가 null 이면 빈 맵을 반환한다")
    void getPopularityScores_nullList_returnsEmptyMap() {
        Map<Long, Double> result = interactionService.getPopularityScores(null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("LocationInteractionCount 결과를 타입 안전하게 매핑한다 (R4)")
    void getPopularityScores_typedProjection_mapsCorrectly() {
        List<Long> ids = List.of(10L, 20L);
        List<LocationInteractionCount> counts = List.of(
                new LocationInteractionCount(10L, 100L),
                new LocationInteractionCount(20L, 1L)
        );
        when(logRepository.countByLocationIdsSince(eq(ids), any(LocalDateTime.class)))
                .thenReturn(counts);

        Map<Long, Double> result = interactionService.getPopularityScores(ids);

        assertThat(result).containsKey(10L).containsKey(20L);
        // 100회 → log10(101)/log10(1001) ≈ 0.667
        assertThat(result.get(10L)).isBetween(0.6, 0.7);
        // 1회 → log10(2)/log10(1001) ≈ 0.100
        assertThat(result.get(20L)).isBetween(0.09, 0.15);
    }

    @Test
    @DisplayName("popularity score 는 1.0 을 초과하지 않는다")
    void getPopularityScores_capped_atOne() {
        List<Long> ids = List.of(99L);
        // 1000회 → log10(1001)/log10(1001) = 1.0
        when(logRepository.countByLocationIdsSince(eq(ids), any()))
                .thenReturn(List.of(new LocationInteractionCount(99L, 1000L)));

        Map<Long, Double> result = interactionService.getPopularityScores(ids);

        assertThat(result.get(99L)).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("30일 이내 데이터만 집계한다 — since 파라미터 검증")
    void getPopularityScores_queriesLast30Days() {
        List<Long> ids = List.of(1L);
        when(logRepository.countByLocationIdsSince(any(), any())).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusDays(30).minusSeconds(1);
        interactionService.getPopularityScores(ids);

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(logRepository).countByLocationIdsSince(eq(ids), sinceCaptor.capture());

        assertThat(sinceCaptor.getValue()).isAfter(before);
    }
}
