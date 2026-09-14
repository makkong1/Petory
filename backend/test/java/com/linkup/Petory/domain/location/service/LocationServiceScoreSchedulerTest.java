package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * score 재계산 배치의 <b>결과</b>를 고정한다. 구현이 JPA 더티 체킹이든 네이티브 UPDATE든
 * 이 테스트는 그대로 통과해야 한다.
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
    @Autowired TransactionTemplate tx;

    @PersistenceContext EntityManager em;

    @Test
    void 재계산은_오염된_score를_공식대로_덮어쓴다() {
        long idx = count("SELECT MIN(idx) FROM locationservice");
        tx.executeWithoutResult(st -> em
                .createNativeQuery("UPDATE locationservice SET score = -1 WHERE idx = :idx")
                .setParameter("idx", idx)
                .executeUpdate());

        scheduler.recalculateAllScores();

        // 오염시킨 행이 실제로 갱신됐다 (save/saveAll 호출 없이도 반영되는지)
        assertThat(countWhere("idx = " + idx + " AND score = -1")).isZero();
        // 전 행이 공식과 일치한다
        assertThat(countWhere("score IS NULL OR score <> (" + SCORE_FORMULA + ")")).isZero();
    }

    private long countWhere(String condition) {
        return count("SELECT COUNT(*) FROM locationservice WHERE " + condition);
    }

    private long count(String sql) {
        return tx.execute(st -> ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }
}
