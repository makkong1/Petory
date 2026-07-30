package com.linkup.Petory.global.database;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 게시글 목록 쿼리의 실행 계획을 건강하게 유지한다.
 *
 * <p>
 * <b>문제</b>: {@code SpringDataJpaBoardRepository.findBoardListItems} 는 작성자가 탈퇴/정지된
 * 게시글을 숨기려고 {@code users} 를 조인한다. 그런데 {@code users.status} 에 값 분포 통계가 없으면
 * 옵티마이저는 {@code status = 'ACTIVE'} 가 1% 정도만 통과시킬 거라고 기본 추측한다(등호 술어당 10% 고정
 * 상수 × 2개). 실제로는 92% 가 통과하므로, 이 93배 오판 하나 때문에 {@code users} 를 드라이빙 테이블로 골라
 * <b>매 페이지마다 조인 결과 전체를 임시테이블에 쌓고 filesort 로 정렬</b>한다.
 *
 * <p>
 * <b>해결</b>: {@code users.status / is_deleted} 에 히스토그램을 만들어 실제 분포를 알려주면, 옵티마이저가
 * {@code board} 를 먼저 읽고 {@code idx_board_deleted_created} 로 정렬 없이 LIMIT 에서 조기 종료한다.
 * 실측(board 50,000 / users 10,001, {@code EXPLAIN ANALYZE}): 히스토그램 제거 시 <b>291ms</b> ↔
 * 적용 시 <b>0.16~0.71ms</b>. (예전 기록의 "0.17s → 0.00s" 는 MySQL 이 소수 둘째 자리로 반올림해 찍은
 * 값이라 0.00s 를 시간으로 인용하면 안 된다.)
 *
 * <p>
 * 행 수 통계만 갱신하는 일반 {@code ANALYZE TABLE} 로는 고쳐지지 않는다 — 값 분포(히스토그램)가 있어야 한다.
 * 실측 근거: {@code docs/analysis/entity-schema/evidence/query-baseline-2026-07-13.md}
 *
 * <p>
 * <b>왜 갱신만으로 끝내지 않는가</b>: 히스토그램은 자동 갱신되지 않고, 실패해도 앱은 정상 동작하며 조용히 느려질 뿐이다.
 * 그래서 이 클래스는 갱신 후 <b>실제 실행 계획을 다시 뽑아 filesort 가 사라졌는지 검증</b>하고, 아니면 ERROR 를 남기고
 * 메트릭을 0 으로 떨어뜨린다. "ANALYZE 를 실행했다" 가 아니라 "계획이 실제로 좋아졌다" 가 성공 조건이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardListQueryPlanMaintainer {

    /** 계획 검증이 의미를 가지려면 최소 이만큼은 있어야 한다. 그 아래면 옵티마이저 선택이 달라도 무해하다. */
    private static final int MIN_ROWS_TO_VERIFY = 1_000;

    /**
     * 실제 목록 쿼리({@code BOARD_LIST_ITEM_SELECT})와 동일한 형태. EXPLAIN 전용이며 실행되지 않는다.
     * 컬럼 목록까지 맞춘 이유: {@code b.content}(LONGTEXT)가 행 크기에 영향을 줘 계획이 달라지기 때문이다.
     */
    private static final String BOARD_LIST_QUERY = """
            SELECT b.idx, b.title, b.content, b.category, b.status, b.created_at, b.is_deleted, b.deleted_at,
                   b.comment_count, b.like_count, b.dislike_count, b.view_count, b.last_reaction_at,
                   u.idx, u.username, u.location
            FROM board b JOIN users u ON u.idx = b.user_idx
            WHERE b.is_deleted = 0 AND u.is_deleted = 0 AND u.status = 'ACTIVE'
            ORDER BY b.created_at DESC LIMIT 20
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    /** 1 = 계획 정상, 0 = filesort 회귀. Prometheus 알람을 이 값에 건다. */
    private final AtomicInteger planHealthy = new AtomicInteger(1);

    @PostConstruct
    void registerMetric() {
        meterRegistry.gauge("petory.board.list_query_plan_healthy", planHealthy);
    }

    /** 기동 직후 1회. 신규 배포·빈 DB 에서 데이터가 쌓인 뒤 처음 뜰 때를 커버한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        refresh();
    }

    /** 매일 17:10. 데이터가 변하면 히스토그램도 낡으므로 주기적으로 다시 만든다. */
    @Scheduled(cron = "${board.query-plan.refresh-cron:0 10 17 * * *}")
    public void refreshOnSchedule() {
        refresh();
    }

    /**
     * 통계·히스토그램을 갱신하고, 실행 계획이 실제로 좋아졌는지 검증한다.
     */
    public void refresh() {
        long startedAt = System.currentTimeMillis();
        log.info("게시글 목록 쿼리 계획 갱신 시작 — ANALYZE + users.status/is_deleted 히스토그램");
        try {
            // 행 수 통계. 이 쿼리를 직접 고치진 못하지만, 대량 INSERT/TRUNCATE 후 낡은 채로 남는다.
            jdbcTemplate.execute("ANALYZE TABLE users, board");

            // 값 분포 통계. 이게 이 쿼리의 실제 해결책이다.
            jdbcTemplate.execute(
                    "ANALYZE TABLE users UPDATE HISTOGRAM ON status, is_deleted WITH 16 BUCKETS");

            verify();
            log.info("게시글 목록 쿼리 계획 갱신 완료 — plan_healthy={}, {}ms 소요",
                    planHealthy.get(), System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            planHealthy.set(0);
            log.error("게시글 목록 쿼리 계획 갱신 실패 ({}ms) — 목록 조회가 느려질 수 있다",
                    System.currentTimeMillis() - startedAt, e);
        }
    }

    private void verify() {
        long boards = count("board");
        if (boards < MIN_ROWS_TO_VERIFY) {
            planHealthy.set(1);
            log.info("게시글 {}건 — 계획 검증 생략 (데이터가 적어 옵티마이저 선택이 무의미)", boards);
            return;
        }

        if (planUsesFilesort()) {
            planHealthy.set(0);
            log.error("게시글 목록 쿼리가 filesort/임시테이블로 회귀했다. 게시글 {}건 전체를 매 페이지마다 정렬하게 된다. "
                    + "users.status 히스토그램이 유효한지 확인할 것 "
                    + "(SELECT * FROM information_schema.column_statistics WHERE table_name='users')", boards);
            return;
        }

        planHealthy.set(1);
        log.info("게시글 목록 쿼리 계획 정상 (게시글 {}건, filesort 없음)", boards);
    }

    /** EXPLAIN 으로 실제 계획을 받아 filesort / 임시테이블 사용 여부를 본다. */
    private boolean planUsesFilesort() {
        String json = jdbcTemplate.queryForObject("EXPLAIN FORMAT=JSON " + BOARD_LIST_QUERY, String.class);
        if (json == null) {
            return true; // 계획을 못 읽었으면 안전한 쪽(문제 있음)으로 본다
        }
        String compact = json.replaceAll("\\s", "");
        return compact.contains("\"using_filesort\":true")
                || compact.contains("\"using_temporary_table\":true");
    }

    private long count(String table) {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }
}
