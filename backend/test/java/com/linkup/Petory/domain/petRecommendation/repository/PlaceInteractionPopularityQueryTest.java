package com.linkup.Petory.domain.petRecommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;

/**
 * 인기 점수 집계가 사용자 단위로 중복 제거되는지 검증한다.
 *
 * <p>
 * ⚠️ 이 검증은 {@code PlaceInteractionServiceTest} 로는 불가능하다 — 거기는 리포지토리를 목으로
 * 대체하므로 JPQL 안의 결함을 볼 수 없다. 집계식이 검증 대상이라 실제 DB 가 필요하다.
 */
@SpringBootTest
@Transactional
class PlaceInteractionPopularityQueryTest {

    /** 실제 locationservice 행과 무관한 값. 집계 쿼리는 FK 없이 locationIdx 로만 묶는다. */
    private static final long LOCATION_A = -9001L;
    private static final long LOCATION_B = -9002L;

    @Autowired PlaceInteractionLogRepository repository;

    private void log(long userIdx, long locationIdx, String type) {
        repository.save(PlaceInteractionLog.builder()
                .userIdx(userIdx)
                .locationIdx(locationIdx)
                .interactionType(type)
                .build());
    }

    @Test
    void 한_사용자가_반복_조회해도_한_번으로_센다() {
        log(1L, LOCATION_A, "VIEW");
        log(1L, LOCATION_A, "VIEW");
        log(1L, LOCATION_A, "VIEW");
        log(1L, LOCATION_A, "NAVIGATE");
        repository.flush();

        long count = countOf(LOCATION_A);

        // 행을 그대로 세면 4가 된다. 혼자서 점수를 올릴 수 있으면 인기 지표가 아니다.
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 서로_다른_사용자는_각각_센다() {
        log(1L, LOCATION_B, "VIEW");
        log(2L, LOCATION_B, "VIEW");
        log(2L, LOCATION_B, "NAVIGATE");
        log(3L, LOCATION_B, "VIEW");
        repository.flush();

        long count = countOf(LOCATION_B);

        // 사용자 3명 · 행 4개. 중복 제거가 "전부 1로 깎기"가 되면 안 된다.
        assertThat(count).isEqualTo(3);
    }

    @Test
    void 기간_밖의_행은_세지_않는다() {
        log(1L, LOCATION_A, "VIEW");
        repository.flush();

        LocalDateTime future = LocalDateTime.now().plusDays(1);
        List<LocationInteractionCount> rows =
                repository.countByLocationIdsSince(List.of(LOCATION_A), future);

        assertThat(rows).isEmpty();
    }

    private long countOf(long locationIdx) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return repository.countByLocationIdsSince(List.of(locationIdx), since).stream()
                .filter(r -> r.locationIdx() == locationIdx)
                .findFirst()
                .map(LocationInteractionCount::count)
                .orElse(0L);
    }
}
