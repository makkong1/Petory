-- ============================================
-- BoardPopularitySnapshot 테이블 인덱스
-- ============================================

-- 인덱스 선택 전략
-- idx_snapshot_period_ranking → 강력 추천 
-- idx_snapshot_recent → 필수
-- idx_snapshot_board_id → 필수

-- 1. 기본 조회 쿼리 최적화
-- 쿼리: findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc
-- WHERE: period_type = ? AND period_start_date = ? AND period_end_date = ?
-- ORDER BY: ranking ASC
CREATE INDEX idx_snapshot_period_ranking 
ON board_popularity_snapshot(period_type, period_start_date, period_end_date, ranking);

-- 2. 범위 조회 쿼리 최적화 (기간 겹치는 경우)
-- 쿼리: findByPeriodTypeAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByRankingAsc
-- WHERE: period_type = ? AND period_start_date <= ? AND period_end_date >= ?
-- ORDER BY: ranking ASC 
-- 주의: 범위 쿼리이므로 period_start_date와 period_end_date 순서가 중요
-- period_start_date는 <= 조건이므로 인덱스에서 먼저 사용
CREATE INDEX idx_snapshot_period_range_ranking 
ON board_popularity_snapshot(period_type, period_start_date, period_end_date, ranking);

-- 3. 최근 스냅샷 조회 최적화
-- 쿼리: findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc
-- WHERE: period_type = ?
-- ORDER BY: period_end_date DESC, ranking ASC
-- LIMIT: 30
CREATE INDEX idx_snapshot_recent 
ON board_popularity_snapshot(period_type, period_end_date DESC, ranking ASC);

-- 4. 삭제 쿼리 최적화
-- 쿼리: deleteByPeriodTypeAndPeriodStartDateAndPeriodEndDate
-- WHERE: period_type = ? AND period_start_date = ? AND period_end_date = ?
-- 위의 idx_snapshot_period_ranking 인덱스로 커버됨 (동일한 조건)

-- 5. board_id 조회 최적화 (FK이지만 인덱스가 없을 수 있음)
-- 특정 게시글의 스냅샷 이력 조회 시 사용
CREATE INDEX idx_snapshot_board_id 
ON board_popularity_snapshot(board_id);

-- ============================================
-- 인덱스 선택 전략 설명
-- ============================================
-- 
-- 1. idx_snapshot_period_ranking: 
--    - 정확한 날짜 매칭 조회에 최적화
--    - 등호 조건 3개 + 정렬 컬럼 포함
-- 
-- 2. idx_snapshot_period_range_ranking:
--    - 범위 조회에 최적화 (기간 겹치는 경우)
--    - period_start_date <= ? AND period_end_date >= ? 조건 처리
-- 
-- 3. idx_snapshot_recent:
--    - 최근 스냅샷 조회에 최적화
--    - period_end_date DESC 정렬로 최신 데이터 빠른 접근
--    - LIMIT 30으로 상위 30개만 조회하므로 매우 효율적
-- 
-- 4. idx_snapshot_board_id:
--    - 특정 게시글의 스냅샷 이력 조회 시 사용
--    - FK이지만 명시적 인덱스 생성 권장
-- 
-- ============================================
-- 성능 최적화 포인트
-- ============================================
-- 
-- - 복합 인덱스 컬럼 순서: 
--   등호 조건 → 범위 조건 → 정렬 컬럼 순서로 배치
-- 
-- - DESC 정렬 인덱스:
--   period_end_date DESC로 최신 데이터 우선 접근
-- 
-- - 인덱스 커버링:
--   필요한 컬럼을 모두 인덱스에 포함하여 테이블 접근 최소화

