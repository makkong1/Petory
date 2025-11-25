-- ============================================
-- BOARD TABLE INDEXES (추가/개선)
-- ============================================

-- 1. 삭제되지 않은 게시글 전체 조회 최적화
-- 쿼리: findAllByIsDeletedFalseOrderByCreatedAtDesc
-- WHERE: is_deleted = false
-- ORDER BY: created_at DESC
CREATE INDEX idx_board_is_deleted_created_at 
ON board(is_deleted, created_at DESC);

-- 2. 카테고리별 삭제되지 않은 게시글 조회 최적화
-- 쿼리: findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc
-- WHERE: category = ? AND is_deleted = false
-- ORDER BY: created_at DESC
CREATE INDEX idx_board_category_is_deleted_created_at 
ON board(category, is_deleted, created_at DESC);

-- 3. 사용자별 삭제되지 않은 게시글 조회 최적화
-- 쿼리: findByUserAndIsDeletedFalseOrderByCreatedAtDesc
-- WHERE: user_idx = ? AND is_deleted = false
-- ORDER BY: created_at DESC
CREATE INDEX idx_board_user_is_deleted_created_at 
ON board(user_idx, is_deleted, created_at DESC);

-- 4. 카테고리 + 기간 범위 조회 최적화 (기존 인덱스로 커버 가능하지만 DESC 정렬 추가 권장)
-- 쿼리: findByCategoryAndCreatedAtBetween
-- WHERE: category = ? AND created_at BETWEEN ? AND ?
-- 기존 idx_board_category_created_at로 커버 가능하지만, 범위 조회 시 DESC 정렬이 필요하면:
-- CREATE INDEX idx_board_category_created_at_desc ON board(category, created_at DESC);

-- 5. 기간 범위 카운트 조회 최적화 (기존 인덱스로 커버 가능)
-- 쿼리: countByCreatedAtBetween
-- WHERE: created_at BETWEEN ? AND ?
-- 기존 idx_board_created_at_desc로 커버 가능

-- 6. 키워드 검색 쿼리 최적화 (FULLTEXT + is_deleted 필터링)
-- 쿼리: searchByKeyword
-- WHERE: is_deleted = false AND (title LIKE '%keyword%' OR content LIKE '%keyword%')
-- ORDER BY: created_at DESC
-- 기존 FULLTEXT 인덱스 + idx_board_is_deleted_created_at 조합 사용
-- 또는 is_deleted를 포함한 복합 인덱스 고려:
CREATE INDEX idx_board_is_deleted_created_at_desc 
ON board(is_deleted, created_at DESC);


-- ============================================
-- 기존 인덱스 정리
-- ============================================
-- idx_board_created_at_desc: 전체 게시글 최신순 조회
-- idx_board_category_created_at: 카테고리별 게시글 조회
-- idx_board_user_idx_created_at: 사용자별 게시글 조회
-- idx_board_title_content (FULLTEXT): 제목/내용 검색


-- ============================================
-- 인덱스 선택 전략
-- ============================================
-- 
-- 1. idx_board_is_deleted_created_at:
--    - 삭제되지 않은 게시글 조회에 최적화
--    - is_deleted = false 조건이 많은 쿼리에 유용
-- 
-- 2. idx_board_category_is_deleted_created_at:
--    - 카테고리별 + 삭제되지 않은 게시글 조회 최적화
--    - 등호 조건(category, is_deleted) + 정렬(created_at) 순서
-- 
-- 3. idx_board_user_is_deleted_created_at:
--    - 사용자별 + 삭제되지 않은 게시글 조회 최적화
--    - FK(user_idx) + 필터(is_deleted) + 정렬(created_at) 순서
-- 
-- 4. DESC 정렬 인덱스:
--    - 최신순 조회가 많으므로 DESC 정렬 인덱스 권장
--    - MySQL 8.0+ 에서는 DESC 인덱스 지원