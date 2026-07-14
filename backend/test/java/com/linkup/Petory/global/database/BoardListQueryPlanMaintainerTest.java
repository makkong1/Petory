package com.linkup.Petory.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 게시글 목록 쿼리가 filesort 계획으로 회귀하지 않는지 지키는 테스트.
 *
 * <p>
 * 배경: {@code users.status} 에 히스토그램이 없으면 옵티마이저가 그 필터를 1% 선택도로 오판해
 * {@code users} 를 먼저 읽고, 조인 결과 전체를 임시테이블에 쌓아 filesort 로 정렬한다.
 * 20행을 돌려주려고 게시글 전체를 정렬하는 셈이다.
 *
 * <p>
 * 이 테스트가 중요한 이유: 히스토그램은 자동 갱신되지 않고, <b>실패해도 앱은 정상 동작한다.</b> 조용히 느려질 뿐이라
 * 아무도 모른다. 그래서 계획 자체를 검사한다.
 *
 * <p>
 * <b>2단계 구조</b>가 핵심이다:
 * <ol>
 * <li>히스토그램을 지우고 → 계획에 filesort 가 <b>실제로 나타나는지 먼저 확인</b>한다.
 * 이게 없으면, 테스트 데이터가 부족해 애초에 나쁜 계획이 안 나오는 경우에도 2단계가 그냥 통과해
 * <b>아무것도 검증하지 못한 채 초록불</b>이 켜진다.</li>
 * <li>운영 코드({@link BoardListQueryPlanMaintainer})를 실행하고 → filesort 가 사라졌는지 확인한다.</li>
 * </ol>
 *
 * <p>
 * 실측 근거: {@code docs/analysis/entity-schema/evidence/query-baseline-2026-07-13.md}
 */
@SpringBootTest
@DisplayName("게시글 목록 쿼리 계획 — filesort 회귀 방지")
class BoardListQueryPlanMaintainerTest {

    /** 500명 / 2,500건이면 나쁜 계획이 재현된다(실측). 그 이상은 CI 시간만 늘 뿐이다. */
    private static final int USERS = 500;
    private static final int BOARDS = 2_500;

    private static final String MARKER = "planprobe_";

    private static final String BOARD_LIST_QUERY = """
            SELECT b.idx, b.title, b.content, b.category, b.status, b.created_at, b.is_deleted, b.deleted_at,
                   b.comment_count, b.like_count, b.dislike_count, b.view_count, b.last_reaction_at,
                   u.idx, u.username, u.location
            FROM board b JOIN users u ON u.idx = b.user_idx
            WHERE b.is_deleted = 0 AND u.is_deleted = 0 AND u.status = 'ACTIVE'
            ORDER BY b.created_at DESC LIMIT 20
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BoardListQueryPlanMaintainer maintainer;

    @BeforeEach
    void seed() {
        cleanUp();
        jdbcTemplate.execute("SET SESSION cte_max_recursion_depth = 1000000");

        jdbcTemplate.update("""
                INSERT INTO users (id, username, email, password, role, status, location,
                                   warning_count, pet_coin_balance)
                WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < ?)
                SELECT CONCAT(?, n), CONCAT(?, n), CONCAT(?, n, '@planprobe.local'),
                       'x', 'USER', 'ACTIVE', '서울', 0, 0
                FROM s
                """, USERS, MARKER, MARKER, MARKER);

        Long firstUser = jdbcTemplate.queryForObject(
                "SELECT MIN(idx) FROM users WHERE email LIKE ?", Long.class, MARKER + "%");

        // content 는 LONGTEXT 다. 행 크기가 계획에 영향을 주므로 실제와 비슷하게 채운다.
        jdbcTemplate.update("""
                INSERT INTO board (user_idx, title, content, category, status, created_at)
                WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < ?)
                SELECT ? + MOD(n * 7919, ?), CONCAT(?, n),
                       REPEAT(CONCAT('본문 ', n, ' '), 20), 'FREE', 'ACTIVE',
                       NOW() - INTERVAL MOD(n, 730) DAY - INTERVAL MOD(n, 1440) MINUTE
                FROM s
                """, BOARDS, firstUser, USERS, MARKER);

        jdbcTemplate.execute("ANALYZE TABLE users, board");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM board WHERE user_idx IN (SELECT idx FROM users WHERE email LIKE ?)",
                MARKER + "%");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", MARKER + "%");
    }

    @Test
    @DisplayName("히스토그램이 없으면 filesort 계획이 나오고, Maintainer 실행 후에는 사라진다")
    void maintainerRemovesFilesortFromBoardListPlan() {
        // 1) 이 테스트가 진짜 문제를 재현하는지 먼저 증명한다.
        // 여기서 filesort 가 안 나오면 아래 검증은 아무것도 보장하지 못한다.
        dropHistogram();
        assertThat(planUsesFilesort())
                .as("히스토그램 없이는 filesort 계획이 나와야 한다 — 안 나오면 이 테스트는 회귀를 잡지 못한다")
                .isTrue();

        // 2) 운영 코드를 실행하면 계획이 고쳐져야 한다.
        maintainer.refresh();

        assertThat(planUsesFilesort())
                .as("Maintainer 실행 후에는 filesort/임시테이블이 없어야 한다")
                .isFalse();
    }

    private void dropHistogram() {
        jdbcTemplate.execute("ANALYZE TABLE users DROP HISTOGRAM ON status, is_deleted");
    }

    private boolean planUsesFilesort() {
        String json = jdbcTemplate.queryForObject(
                "EXPLAIN FORMAT=JSON " + BOARD_LIST_QUERY, String.class);
        assertThat(json).isNotNull();
        String compact = json.replaceAll("\\s", "");
        return compact.contains("\"using_filesort\":true")
                || compact.contains("\"using_temporary_table\":true");
    }
}
