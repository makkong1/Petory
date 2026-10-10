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
 * <b>무엇을 지키는가</b>: 목록 1단계 쿼리({@code SpringDataJpaBoardRepository.findVisibleBoardIds})가
 * 커버링 인덱스 {@code idx_board_visible_created (is_deleted, author_visible, created_at DESC)} 를 타고
 * <b>정렬 없이 LIMIT 에서 조기 종료</b>하는지 본다. 이 전제가 깨지면 20행을 돌려주려고 게시글 전체를
 * 임시테이블에 쌓아 filesort 로 정렬하게 된다. 깨지는 경로는 두 가지다 — 인덱스가 없어지거나,
 * 정렬 방향이 인덱스 순서와 어긋나거나(세컨더리 인덱스는 {@code (키…, PK)} 로 저장되므로
 * {@code created_at DESC} 안에서 PK 는 ASC 순이다. {@code idx DESC} 로 쓰면 filesort 가 붙는다).
 *
 * <p>
 * <b>왜 히스토그램도 같이 갱신하는가</b>: 2026-07-13 당시 목록은 작성자가 탈퇴/정지된 글을 숨기려고
 * {@code users} 를 조인했고, {@code users.status} 에 값 분포 통계가 없으면 옵티마이저가
 * {@code status = 'ACTIVE'} 를 1% 선택도로 오판했다(등호 술어당 10% 고정 상수 × 2개). 실제로는 92% 가
 * 통과하므로 이 93배 오판 하나 때문에 {@code users} 를 드라이빙 테이블로 골라 filesort 가 났다.
 * 실측(board 50,000 / users 10,001, {@code EXPLAIN ANALYZE}): 히스토그램 제거 시 <b>291ms</b> ↔
 * 적용 시 <b>0.16~0.71ms</b>. (예전 기록의 "0.17s → 0.00s" 는 MySQL 이 소수 둘째 자리로 반올림해 찍은
 * 값이라 0.00s 를 시간으로 인용하면 안 된다.) 행 수 통계만 갱신하는 일반 {@code ANALYZE TABLE} 로는
 * 고쳐지지 않는다 — 값 분포가 있어야 한다. 근거:
 * {@code docs/analysis/entity-schema/evidence/query-baseline-2026-07-13.md}
 *
 * <p>
 * 그 뒤 Flyway {@code V6} 이 작성자 노출 조건을 {@code board.author_visible} 로 비정규화해
 * <b>목록 경로에서 조인 자체가 사라졌다</b>. 그래서 <b>게시글 목록은 더 이상 이 히스토그램에 의존하지 않는다.</b>
 * 그럼에도 갱신을 남겨둔 이유는, 아직 {@code JOIN users ... u.status <> BANNED} 형태로 도는 목록들이
 * 있기 때문이다(care 요청 목록 · 케어 댓글 · 실종글 댓글). 그중 care 요청 목록은
 * {@code Page<>} + {@code ORDER BY cr.created_at DESC} + 조인이라 2026-07-13 과 같은 모양이다
 * — 다만 그 경로들의 실행 계획은 <b>아직 측정하지 않았다</b>. 즉 이 클래스의 두 일은 지금 독립적이다:
 * 갱신은 다른 도메인 목록들을 위한 것이고, 검증은 게시글 목록을 위한 것이다.
 *
 * <p>
 * <b>왜 갱신만으로 끝내지 않는가</b>: 통계도 인덱스 전제도 자동으로 지켜지지 않고, 깨져도 앱은 정상 동작하며
 * 조용히 느려질 뿐이다. 그래서 이 클래스는 갱신 후 <b>실제 실행 계획을 다시 뽑아 filesort 가 없는지 검증</b>하고,
 * 아니면 ERROR 를 남기고 메트릭을 0 으로 떨어뜨린다. "ANALYZE 를 실행했다" 가 아니라 "계획이 실제로 건강하다" 가
 * 성공 조건이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardListQueryPlanMaintainer {

    /** 계획 검증이 의미를 가지려면 최소 이만큼은 있어야 한다. 그 아래면 옵티마이저 선택이 달라도 무해하다. */
    private static final int MIN_ROWS_TO_VERIFY = 1_000;

    /**
     * 실제 서비스되는 목록 1단계 쿼리({@code SpringDataJpaBoardRepository.findVisibleBoardIds})와 동일한
     * 형태. EXPLAIN 전용이며 실행되지 않는다. 깊은 OFFSET 이 아니라 1페이지 형태로 보는 이유: 계획(인덱스
     * 사용 · filesort 유무)은 OFFSET 값에 따라 달라지지 않는다.
     */
    static final String BOARD_LIST_QUERY = """
            SELECT idx FROM board
             WHERE is_deleted = 0 AND author_visible = 1
             ORDER BY created_at DESC, idx ASC
             LIMIT 20
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

            // 값 분포 통계. 게시글 목록은 V6 비정규화로 더 이상 이걸 필요로 하지 않지만,
            // 아직 users 를 조인하는 목록들(care 요청 · 댓글)이 쓴다. 클래스 javadoc 참고.
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
                    + "커버링 인덱스 전제가 깨졌는지 확인할 것 — 인덱스 존재 여부"
                    + "(SHOW INDEX FROM board WHERE Key_name='idx_board_visible_created')와 "
                    + "목록 쿼리의 정렬 방향(created_at DESC, idx ASC 여야 한다)", boards);
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
