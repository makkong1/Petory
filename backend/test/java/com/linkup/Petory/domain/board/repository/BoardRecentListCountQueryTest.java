package com.linkup.Petory.domain.board.repository;

import com.linkup.Petory.domain.board.entity.Board;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최신 게시글 상위 N건 조회가 COUNT 쿼리를 동반하지 않는지 지키는 회귀 테스트.
 *
 * <p>소비처({@code BoardPopularityService#buildRecentBoardFallback})는 상위 10건만 쓰고 총건수는 버린다.
 * 반환 타입이 {@code Page}였을 때는 그 버려지는 총건수를 위해 COUNT 가 한 번 더 나갔고,
 * 본문 쿼리의 users 조인을 그대로 물고 board 5만 행을 훑었다(개발 DB EXPLAIN ANALYZE 136ms).
 * {@code Page} 로 되돌아가면 이 테스트의 JDBC 문 수가 1 -> 2 로 늘어 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoardRecentListCountQueryTest {

    @Autowired
    private SpringDataJpaBoardRepository boardRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("최신 게시글 상위 N건 조회는 JDBC 문 1개(SELECT)만 실행한다 — COUNT 없음")
    void recentBoardsIssueNoCountQuery() {
        Session session = entityManager.unwrap(Session.class);
        Statistics statistics = session.getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        entityManager.flush();
        entityManager.clear();

        long before = statistics.getPrepareStatementCount();
        List<Board> recent = boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(PageRequest.of(0, 10));
        long executed = statistics.getPrepareStatementCount() - before;

        assertThat(recent).hasSizeLessThanOrEqualTo(10);
        assertThat(executed).isEqualTo(1);
    }
}
