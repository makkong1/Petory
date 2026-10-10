package com.linkup.Petory.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 게시글 목록 쿼리가 filesort 계획으로 회귀하지 않는지 지키는 테스트.
 *
 * <p>
 * 검사 대상은 <b>실제 서비스되는 1단계 쿼리</b>다 — {@link BoardListQueryPlanMaintainer#BOARD_LIST_QUERY}
 * 를 그대로 가져다 쓴다. 쿼리 문자열을 여기 복사해두면 운영 쿼리가 바뀔 때 테스트만 옛 형태를 감시하게 된다
 * (실제로 Flyway {@code V6} 비정규화 뒤 그런 상태로 남아 있었다).
 *
 * <p>
 * 이 테스트가 중요한 이유: 커버링 인덱스 전제는 자동으로 지켜지지 않고, <b>깨져도 앱은 정상 동작한다.</b>
 * 조용히 느려질 뿐이라 아무도 모른다. 그래서 계획 자체를 검사한다.
 *
 * <p>
 * <b>2단계 구조</b>가 핵심이다:
 * <ol>
 * <li>정렬 방향만 뒤집은 쿼리({@code idx DESC})로 → filesort 가 <b>실제로 감지되는지 먼저 확인</b>한다.
 * 이게 없으면, 테스트 데이터가 부족해 애초에 나쁜 계획이 안 나오는 경우에도 2단계가 그냥 통과해
 * <b>아무것도 검증하지 못한 채 초록불</b>이 켜진다. 방향을 빨간불로 쓰는 근거: 세컨더리 인덱스는
 * {@code (키…, PK)} 로 저장되므로 {@code created_at DESC} 안에서 PK 는 ASC 순이고, {@code idx DESC} 는
 * 인덱스 순서와 어긋나 filesort 가 붙는다(실측: 1페이지가 48,000행 정렬로 15.8ms, ASC 는 0.02ms).</li>
 * <li>운영 코드({@link BoardListQueryPlanMaintainer})를 실행하고 → 실제 쿼리에 filesort 가 없다고
 * 판정하는지(메트릭 {@code = 1}) 확인한다.</li>
 * </ol>
 *
 * <p>
 * 실측 근거: {@code docs/analysis/entity-schema/evidence/query-baseline-2026-07-13.md}
 */
@SpringBootTest
@DisplayName("게시글 목록 쿼리 계획 — filesort 회귀 방지")
class BoardListQueryPlanMaintainerTest {

    /**
     * 게시글은 {@code MIN_ROWS_TO_VERIFY}(1,000) 를 넘겨야 Maintainer 가 검증을 생략하지 않는다.
     * 2,500 건이면 충분하고 그 이상은 CI 시간만 늘 뿐이다. 회원 수는 board 의 FK 를 채우기 위한 것이다.
     */
    private static final int USERS = 500;
    private static final int BOARDS = 2_500;

    private static final String MARKER = "planprobe_";

    /** 운영 쿼리와 어긋나지 않게 상수를 그대로 가져온다. */
    private static final String BOARD_LIST_QUERY = BoardListQueryPlanMaintainer.BOARD_LIST_QUERY;

    /** 빨간불용 — 보조 정렬키 방향만 뒤집었다. 인덱스 순서와 어긋나 filesort 가 붙어야 한다. */
    private static final String FILESORT_QUERY = BOARD_LIST_QUERY.replace("idx ASC", "idx DESC");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BoardListQueryPlanMaintainer maintainer;

    @Autowired
    private MeterRegistry meterRegistry;

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
    @DisplayName("실제 목록 쿼리는 filesort 없이 커버링 인덱스를 타고, Maintainer 가 정상으로 판정한다")
    void maintainerVerifiesRealBoardListPlan() {
        // 1) 이 테스트가 filesort 를 진짜 감지할 수 있는지 먼저 증명한다.
        // 여기서 false 가 나오면 아래 검증은 아무것도 보장하지 못한다.
        assertThat(planUsesFilesort(FILESORT_QUERY))
                .as("정렬 방향을 뒤집으면 filesort 가 나와야 한다 — 안 나오면 이 테스트는 회귀를 잡지 못한다")
                .isTrue();

        // 2) 운영 코드가 실제 쿼리를 정상으로 판정해야 한다.
        maintainer.refresh();

        assertThat(planUsesFilesort(BOARD_LIST_QUERY))
                .as("서비스되는 목록 쿼리에는 filesort/임시테이블이 없어야 한다")
                .isFalse();
        assertThat(meterRegistry.get("petory.board.list_query_plan_healthy").gauge().value())
                .as("Maintainer 가 계획을 정상으로 판정해야 한다")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("히스토그램이 없으면 Maintainer 가 다시 만든다")
    void maintainerRecreatesHistogram() {
        // 게시글 목록은 V6 비정규화로 더 이상 이 히스토그램에 의존하지 않지만,
        // 아직 users 를 조인하는 목록들(care 요청 · 댓글)이 쓴다. 갱신이 멈추면 그쪽이 조용히 느려진다.
        dropHistogram();
        assertThat(histogramColumns()).isZero();

        maintainer.refresh();

        assertThat(histogramColumns())
                .as("users.status / is_deleted 히스토그램 2개가 다시 만들어져야 한다")
                .isEqualTo(2);
    }

    private void dropHistogram() {
        jdbcTemplate.execute("ANALYZE TABLE users DROP HISTOGRAM ON status, is_deleted");
    }

    private int histogramColumns() {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.column_statistics
                 WHERE schema_name = DATABASE() AND table_name = 'users'
                   AND column_name IN ('status', 'is_deleted')
                """, Integer.class);
        return n == null ? 0 : n;
    }

    private boolean planUsesFilesort(String query) {
        String json = jdbcTemplate.queryForObject(
                "EXPLAIN FORMAT=JSON " + query, String.class);
        assertThat(json).isNotNull();
        String compact = json.replaceAll("\\s", "");
        return compact.contains("\"using_filesort\":true")
                || compact.contains("\"using_temporary_table\":true");
    }
}
