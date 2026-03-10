-- ============================================
-- MISSING_PET_COMMENT TABLE INDEXES (추가)
-- ============================================
-- [리팩토링] missing-pet-backend-performance-optimization.md 섹션 9
-- findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc 쿼리 최적화
-- WHERE: board_idx = ? AND is_deleted = false
-- ORDER BY: created_at ASC

CREATE INDEX idx_missing_pet_comment_board_is_deleted
ON missing_pet_comment(board_idx, is_deleted);
