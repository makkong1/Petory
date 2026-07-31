package com.linkup.Petory.global.performance;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    private long rowCountOf(String table) {
        Object count = entityManager.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult();
        return ((Number) count).longValue();
    }

    /**
     * 실행계획 단언은 <b>데이터가 충분할 때만</b> 의미가 있다.
     *
     * <p>
     * 옵티마이저는 행 수와 통계로 계획을 고르므로, 빈 테이블에서는 인덱스를 안 타거나 다른 인덱스를
     * 골라도 그게 정상이다. 실제로 CI 가 이것 때문에 깨졌다 — CI 는 빈 MySQL 에 Flyway 로 스키마만
     * 만들고 더미 데이터를 넣지 않는데, 로컬(board 5만 행)에서 통과하던 계획 단언이 CI(0행)에서
     * 실패했다.
     *
     * <p>
     * 그래서 행 수가 기준 미만이면 <b>실패가 아니라 skip</b> 한다. 이 가드가 지키려는 회귀
     * (인덱스를 못 타는 형태로 쿼리가 다시 쓰이는 것)는 어차피 데이터가 있는 환경에서만 드러난다.
     * 데이터와 무관하게 결정적인 것 — 인덱스가 존재하는지 — 은 아래 스키마 검증 테스트들이 맡는다.
     */
    private void requireRowsFor(String table, long minimumRows) {
        long actual = rowCountOf(table);
        assumeTrue(actual >= minimumRows,
                () -> String.format(
                        "%s 가 %d행뿐이라 실행계획 단언을 건너뛴다(필요: %d행 이상). "
                                + "빈 DB(CI)에서는 옵티마이저가 다른 계획을 골라도 정상이다.",
                        table, actual, minimumRows));
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
    private List<String> spatialColumnsOf(String table) {
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager.createNativeQuery(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t "
                        + "AND INDEX_TYPE = 'SPATIAL'")
                .setParameter("t", table)
                .getResultList();
        return columns;
    }

    /**
     * 지도 반경검색 네 도메인이 모두 SPATIAL 인덱스를 갖고 있어야 한다 (2026-07-31 통일).
     *
     * <p>
     * 예전엔 carerequest 하나만 검증했는데, 네 도메인이 같은 2단계 전략
     * ({@code ST_Within} 으로 후보 축소 → {@code ST_Distance_Sphere} 로 정밀 반경)을 쓰므로
     * 가드도 같이 걸어야 한다. 실제로 meetup·missing_pet_board 은 원래
     * {@code (latitude, longitude)} B-tree 였다가 SPATIAL 로 전환한 이력이 있고
     * (missing_pet 은 V5 / 커밋 f010dfc6), 이 인덱스가 사라지면 조용히 풀스캔으로 돌아간다.
     *
     * <p>
     * EXPLAIN 이 아니라 스키마로 검증하는 이유는 실행 계획이 흔들리기 때문이다 — 반경에 따라
     * 옵티마이저가 공간 인덱스를 안 고를 수 있다(측정: meetup 은 박스가 테이블의 약 25% 를
     * 넘는 12km 부터 Table scan). 계획은 상황에 따라 달라져도 인덱스 존재는 결정적이다.
     */
    @Test
    @DisplayName("지도 반경검색 4도메인: geo_point/location 에 SPATIAL 인덱스가 있어야 한다")
    void nearbySearchDomainsHaveSpatialIndex() {
        assertThat(spatialColumnsOf("carerequest"))
                .as("SPATIAL 이 없으면 care 주변검색이 carerequest 를 풀스캔한다 (3,000행 → 208행)")
                .containsExactly("geo_point");
        assertThat(spatialColumnsOf("meetup"))
                .as("SPATIAL 이 없으면 모임 주변검색이 meetup 을 풀스캔한다")
                .containsExactly("geo_point");
        assertThat(spatialColumnsOf("missing_pet_board"))
                .as("SPATIAL 이 없으면 실종제보 홈 추천이 missing_pet_board 를 풀스캔한다 (V5 전환분)")
                .containsExactly("geo_point");
        assertThat(spatialColumnsOf("locationservice"))
                .as("SPATIAL 이 없으면 장소 반경검색이 locationservice 를 풀스캔한다")
                .containsExactly("location");
    }

    /**
     * 공간 인덱스로 대체된 {@code (latitude, longitude)} B-tree 가 되살아나면 안 된다 (V10).
     *
     * <p>
     * B-tree 는 1차원 정렬이라 위도 범위 뒤의 경도가 연속 구간이 되지 못해 탐색에 못 쓰인다.
     * 그래서 두 도메인 다 SPATIAL 로 전환했는데 인덱스만 남아 있었다 — 읽는 쿼리가 없어
     * 17일간 읽기 0회였고, INSERT/UPDATE 비용과 공간만 쓰고 있었다.
     * 되살아난다는 건 누군가 lat/lng BETWEEN 쿼리를 다시 넣었다는 뜻이라 그때 잡아야 한다.
     */
    @Test
    @DisplayName("V10·V11: 읽는 쿼리가 없어 지운 인덱스가 되살아나지 않았다")
    void deadIndexesStayDropped() {
        @SuppressWarnings("unchecked")
        List<String> revived = entityManager.createNativeQuery(
                "SELECT CONCAT(TABLE_NAME, '.', INDEX_NAME) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME IN ("
                        // V10 — SPATIAL 로 대체된 lat/lng B-tree
                        + "'idx_meetup_location', 'idx_missing_pet_location', "
                        // V11 — 호출부가 주석 처리돼 있던 읍면동·도로명 검색 인덱스
                        + "'idx_locationservice_eupmyeondong_deleted_rating', 'idx_road_name_deleted_rating') "
                        + "GROUP BY TABLE_NAME, INDEX_NAME")
                .getResultList();

        assertThat(revived)
                .as("지운 인덱스가 되살아났다. lat/lng BETWEEN 쿼리나 읍면동·도로명 검색이 "
                        + "다시 들어왔다면 인덱스를 되살리는 게 맞고, 아니라면 마이그레이션을 확인할 것.")
                .isEmpty();
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
        requireRowsFor("carerequest", 500);
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
        requireRowsFor("board", 1000);
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

    /**
     * 이 인덱스는 "ESR 규칙 위반" 처럼 보이지만 <b>일부러 그 순서다.</b> 규칙대로 고치면 느려진다.
     *
     * <p>
     * 대상 쿼리는 {@code UserPetIntentSignalRepository.findActiveByUser} 로
     * {@code WHERE user_idx = ? AND expires_at > now ORDER BY created_at DESC LIMIT 10} 이다.
     * 등가 → 범위 → 정렬(E→R→S) 이라 계획에 {@code Sort:} 가 붙고, 교과서 ESR(E→S→R) 은
     * {@code (user_idx, created_at, expires_at)} 를 권한다.
     *
     * <p>
     * <b>20,000행(사용자 200명 × 100건, TTL 1~14일)을 넣고 양쪽을 재본 결과는 반대였다.</b>
     * <ul>
     * <li>유효 12건(LIMIT 을 채움) — 현재 0.024ms / ESR 0.014ms → ESR 근소 우위</li>
     * <li><b>유효 1건(LIMIT 을 못 채움) — 현재 1행 0.161ms / ESR 100행 1.1ms</b></li>
     * </ul>
     * ESR 의 조기종료는 매치가 LIMIT 이상일 때만 작동한다. 10건을 못 채우면 엔진이 그 사용자의
     * 인덱스 구간 끝까지 역주행하는데, 범위 조건이 {@code Filter:} 라 스캔을 멈추지 못한다.
     * 유효 신호는 사용자당 1~12건이고 LIMIT 은 10이라 <b>대부분의 사용자가 후자</b>다.
     *
     * <p>
     * 즉 범위 조건이 결과를 LIMIT 아래로 줄일 만큼 선택적이면 범위를 앞에 두는 쪽이 이긴다.
     * {@code Sort:} 가 보인다고 결함이 아니다 — 정렬 대상이 10건이면 그 정렬은 사실상 공짜다.
     * 이 테스트는 <b>나중에 누가 "ESR 위반이네" 하고 순서를 바꾸는 것을 막으려고</b> 있다.
     *
     * <p>
     * 근거: docs/interview/concepts/01_DB_인덱스.md §3-3
     */
    @Test
    @DisplayName("신호 조회: idx_user_signal_active 는 (user_idx, expires_at, created_at) 순서여야 한다")
    void signalLookupKeepsRangeBeforeSort() {
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager.createNativeQuery(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_pet_intent_signal' "
                        + "AND INDEX_NAME = 'idx_user_signal_active' ORDER BY SEQ_IN_INDEX")
                .getResultList();

        assertThat(columns)
                .as("ESR(user_idx, created_at, expires_at) 로 바꾸면 LIMIT 을 못 채우는 사용자에서 "
                        + "사용자 구간 전체를 역주행한다 (실측 1행 0.161ms → 100행 1.1ms). "
                        + "계획에 Sort: 가 보이는 건 알고 있고, 그 정렬 대상은 1~12건이라 무시할 만하다.")
                .containsExactly("user_idx", "expires_at", "created_at");
    }

    @Test
    @DisplayName("admin 사용자 목록: created_at 인덱스를 타고 filesort 가 없다")
    void adminUserListUsesCreatedAtIndex() {
        requireRowsFor("users", 500);
        String plan = explain("SELECT u.idx FROM users u ORDER BY u.created_at DESC LIMIT 20");

        assertThat(plan)
                .as("users.created_at 인덱스가 없으면 1만 행을 전부 읽고 전부 정렬한다.\n계획:\n%s", plan)
                .contains("idx_users_created_at");
        assertThat(plan)
                .as("정렬용 인덱스를 타면 filesort 가 필요 없다.\n계획:\n%s", plan)
                .doesNotContain("Sort:");
    }
}
