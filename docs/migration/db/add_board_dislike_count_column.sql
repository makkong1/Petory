-- ============================================
-- Board 테이블에 dislike_count 컬럼 추가
-- ============================================
-- ReactionService reactToBoard 시 dislikeCount 실시간 업데이트용
-- Board 엔티티 dislikeCount 필드와 매핑
-- ============================================

ALTER TABLE board ADD COLUMN dislike_count INT DEFAULT 0;

-- 기존 데이터 backfill (board_reaction에서 집계)
UPDATE board b
SET dislike_count = (
    SELECT COUNT(*)
    FROM board_reaction br
    WHERE br.board_idx = b.idx
      AND br.reaction_type = 'DISLIKE'
);
