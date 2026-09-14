package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.SpringDataJpaLocationServiceRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * score 재계산 배치의 <b>결과</b>를 고정한다. 구현이 JPA 더티 체킹이든 네이티브 UPDATE든
 * 이 테스트는 그대로 통과해야 한다.
 *
 * <p>
 * 검증할 행을 직접 만든다 — 기존 행에 기대면 더미 데이터가 없는 CI 에서 깨진다.
 */
@SpringBootTest
class LocationServiceScoreSchedulerTest {

    /**
     * 프로덕션({@code SpringDataJpaLocationServiceRepository.SCORE_FORMULA})과 같은 공식을
     * <b>일부러 복제해</b> 둔다. 상수를 그대로 참조하면 공식이 바뀌어도 테스트가 따라 바뀌어
     * 아무것도 못 잡는다(동어반복).
     */
    private static final String SCORE_FORMULA =
            "0.5 * COALESCE(rating, 0) * LOG10(COALESCE(review_count, 0) + 1) "
                    + "+ 0.2 * IF(pet_friendly = 1, 1.0, 0.0)";

    @Autowired LocationServiceScoreScheduler scheduler;
    @Autowired SpringDataJpaLocationServiceRepository repository;
    @Autowired TransactionTemplate tx;

    @PersistenceContext EntityManager em;

    @Test
    void 재계산은_오염된_score를_공식대로_덮어쓴다() {
        // rating 4.0, reviewCount 9, petFriendly → 0.5 x 4.0 x log10(10) + 0.2 = 2.2
        Long idx = tx.execute(st -> repository.save(LocationService.builder()
                .name("score-scheduler-test")
                .latitude(37.5).longitude(127.0)
                .rating(4.0).reviewCount(9).petFriendly(true)
                .score(-1.0)
                .build()).getIdx());

        try {
            scheduler.recalculateAllScores();

            // 공식과 무관하게 손으로 계산한 값 — 공식 자체가 틀리면 여기서 잡힌다
            assertThat(scoreOf(idx)).isEqualTo(2.2, within(1e-9));
            // 전 행이 공식과 일치한다
            assertThat(countWhere("score IS NULL OR score <> (" + SCORE_FORMULA + ")")).isZero();
        } finally {
            tx.executeWithoutResult(st -> repository.deleteById(idx));
        }
    }

    private double scoreOf(Long idx) {
        return tx.execute(st -> ((Number) em
                .createNativeQuery("SELECT score FROM locationservice WHERE idx = :idx")
                .setParameter("idx", idx)
                .getSingleResult()).doubleValue());
    }

    private long countWhere(String condition) {
        return tx.execute(st -> ((Number) em
                .createNativeQuery("SELECT COUNT(*) FROM locationservice WHERE " + condition)
                .getSingleResult()).longValue());
    }
}
