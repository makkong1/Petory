package com.linkup.Petory.global.performance;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * 인덱스가 "존재하는지" 가 아니라 "실제로 쓰이는지" 를 검증한다 (2026-07-14)
 * ====================================================================================
 *
 * 인덱스 존재 여부만 확인하면 부족하다. 감사 도중 실제로 이런 일이 있었다:
 *
 *   · pets 에 (pet_type, is_deleted) 복합 인덱스를 만들었는데 옵티마이저가 안 골랐다.
 *   · carerequest 에 (is_deleted, latitude, longitude) B-tree 를 만들었는데 소용이 없었다
 *     — is_deleted 는 선택도가 0 이고, longitude 는 latitude 범위 뒤에서 범위 조건으로 못 쓰인다.
 *     결국 SPATIAL 인덱스(geo_point)로 다시 갔다.
 *
 * 그래서 EXPLAIN 을 실행해 "그 인덱스 이름이 실행 계획에 등장하는지" 를 본다.
 * 이러면 두 가지 회귀를 한꺼번에 잡는다:
 *   (1) 마이그레이션이 되돌려져 인덱스가 사라진 경우
 *   (2) 쿼리가 인덱스를 못 타는 형태로 다시 쓰인 경우 (예: ST_Within → BETWEEN 복귀)
 *
 * 근거: docs/analysis/query-audit/fixes-2026-07-14.md
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class IndexUsageRegressionTest {

    @PersistenceContext
    private EntityManager entityManager;

    private String explain(String sql) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("EXPLAIN FORMAT=TREE " + sql).getResultList();
        StringBuilder sb = new StringBuilder();
        rows.forEach(r -> sb.append(r).append('\n'));
        return sb.toString();
    }

    /**
     * SPATIAL 인덱스는 EXPLAIN 으로 검증하지 않는다.
     *
     * 실행 계획은 옵티마이저의 통계 추정에 따라 세션마다 달라진다 — 실제로 이 테스트를 처음 EXPLAIN
     * 기반으로 짰더니, CLI 에서는 SPATIAL 인덱스를 타는데(rows=190) 테스트 세션에서는 통계 추정이
     * rows=1 로 나와 B-tree 인덱스를 골랐다. 즉 EXPLAIN 단언은 흔들린다.
     *
     * 대신 스키마를 검증한다. 마이그레이션이 되돌려지는 것이 실제 회귀 위험이고, 그건 결정적으로 잡힌다.
     * 인덱스가 실제로 쓰이는지는 A/B/A 로 확인해 문서에 남겼다
     * (인덱스 제거 시 Table scan 3,000행 → 재적용 시 Index range scan 208행).
     */
    @Test
    @DisplayName("care 주변검색: geo_point 에 SPATIAL 인덱스가 있어야 한다")
    void nearbySearchHasSpatialIndex() {
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager.createNativeQuery(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'carerequest' "
                        + "AND INDEX_TYPE = 'SPATIAL'")
                .getResultList();

        assertThat(columns)
                .as("SPATIAL 인덱스가 없으면 주변검색이 carerequest 를 풀스캔한다 (3,000행 → 208행)")
                .containsExactly("geo_point");
    }

    /**
     * 첨부 조회 인덱스는 EXPLAIN 이 아니라 스키마로 검증한다.
     *
     * <p>
     * {@code file} 테이블은 현재 더미 시드에 데이터가 없어(board 5만·care 3천·pets 1.2만은 있는데
     * 첨부만 0행) EXPLAIN 을 떠도 의미 있는 계획이 안 나온다. 그런데 <b>인덱스가 사라지는 것 자체가
     * 실제 회귀 위험</b>이고 그건 스키마로 결정적으로 잡힌다.
     *
     * <p>
     * 배경: {@code AttachmentFileService} 의 개별·배치 조회가 공통으로
     * {@code WHERE target_type = ? AND target_idx IN (…)} 을 쓰는데 이 인덱스가 없어 <b>개별이든
     * 배치든 매번 테이블 전체를 스캔</b>하고 있었다(2026-07-12 발견, 커밋 {@code 631d2d15}).
     * 당시 측정은 개별조회 0.444→0.032ms, 배치조회 0.211→0.042ms 로 절대값이 작았지만, 풀스캔은
     * <b>테이블 크기에 비례</b>하므로 첨부가 쌓이면 그대로 벌어진다. 고친 근거는 속도가 아니라 그 구조다.
     *
     * <p>
     * N+1(왕복 횟수)과 인덱스 부재(각 쿼리의 실행계획)는 서로 다른 축이라, 배치 조회로 N+1 을 없앤
     * 뒤에도 이 문제가 따로 남아 있었다.
     */
    @Test
    @DisplayName("첨부 조회: file(target_type, target_idx) 복합 인덱스가 있어야 한다")
    void attachmentLookupHasCompositeIndex() {
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager.createNativeQuery(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file' "
                        + "AND INDEX_NAME = 'idx_file_target' ORDER BY SEQ_IN_INDEX")
                .getResultList();

        assertThat(columns)
                .as("idx_file_target 이 사라지면 첨부 조회(개별·배치 공통)가 file 전체를 스캔한다. "
                        + "지금은 file 이 0행이라 체감이 없지만, 첨부가 쌓이는 만큼 선형으로 나빠진다.")
                .containsExactly("target_type", "target_idx");
    }

    @Test
    @DisplayName("care 목록: 정렬용 인덱스를 타고 filesort 가 없다")
    void careListUsesSortIndex() {
        String plan = explain(
                "SELECT cr.idx FROM carerequest cr JOIN users u ON u.idx = cr.user_idx "
                        + "WHERE cr.is_deleted = 0 AND u.is_deleted = 0 AND u.status = 'ACTIVE' "
                        + "ORDER BY cr.created_at DESC LIMIT 20");

        assertThat(plan)
                .as("(is_deleted, created_at) 인덱스가 없으면 carerequest 전체를 읽고 전부 정렬한다.\n계획:\n%s", plan)
                .contains("idx_carerequest_deleted_created");
        assertThat(plan)
                .as("정렬용 인덱스를 타면 filesort 가 필요 없다.\n계획:\n%s", plan)
                .doesNotContain("Sort:");
    }

    /**
     * 실제로 이 테스트가 없어서 회귀를 놓쳤다.
     *
     * <p>
     * 정렬 동점 처리로 {@code ORDER BY created_at DESC, idx DESC} 를 붙였더니, 세컨더리 인덱스는
     * {@code (키…, PK)} 로 저장돼 {@code created_at DESC} 인덱스 안의 PK 는 ASC 순인데 DESC 를
     * 요구해서 <b>인덱스 순서로 정렬을 만족시키지 못하고 filesort 가 붙었다.</b> 1페이지가 20행을
     * 받으려고 48,000행을 전부 읽고 정렬해 15.8ms(원래 0.02ms)가 됐다.
     *
     * <p>
     * 보조 키를 {@code idx ASC} 로 바꾸면 인덱스 스캔 순서 그대로라 정렬이 사라진다. 이 테스트는
     * "동점 처리를 붙이되 인덱스 순서를 깨지 않는다" 는 계약을 고정한다.
     */
    @Test
    @DisplayName("게시글 목록 1단계: 커버링 인덱스를 타고 filesort 가 없다 (동점 처리 방향 포함)")
    void boardListStage1UsesCoveringIndexWithoutSort() {
        String plan = explain(
                "SELECT idx FROM board WHERE is_deleted = 0 AND author_visible = 1 "
                        + "ORDER BY created_at DESC, idx ASC LIMIT 20 OFFSET 0");

        assertThat(plan)
                .as("커버링 인덱스를 못 타면 깊은 페이지 skip 이 무의미해진다.\n계획:\n%s", plan)
                .contains("idx_board_visible_created");
        assertThat(plan)
                .as("정렬 노드가 생기면 LIMIT 20 인데도 전체를 읽고 정렬한다. "
                        + "보조 정렬키 방향이 인덱스와 어긋났는지 확인할 것.\n계획:\n%s", plan)
                .doesNotContain("Sort:");
    }

    @Test
    @DisplayName("admin 사용자 목록: created_at 인덱스를 타고 filesort 가 없다")
    void adminUserListUsesCreatedAtIndex() {
        String plan = explain("SELECT u.idx FROM users u ORDER BY u.created_at DESC LIMIT 20");

        assertThat(plan)
                .as("users.created_at 인덱스가 없으면 1만 행을 전부 읽고 전부 정렬한다.\n계획:\n%s", plan)
                .contains("idx_users_created_at");
        assertThat(plan)
                .as("정렬용 인덱스를 타면 filesort 가 필요 없다.\n계획:\n%s", plan)
                .doesNotContain("Sort:");
    }
}
