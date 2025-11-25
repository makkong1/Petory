-- ============================================
-- 테스트 DB 데이터 전체 삭제 SQL
-- ============================================
-- 주의: 이 쿼리는 petory_test DB의 모든 데이터를 삭제합니다!
-- 실행 전 반드시 확인하세요!

USE petory_test;

-- 외래키 체크 임시 해제 (더 빠른 삭제)
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 데이터 삭제 (외래키 순서 고려)
-- ============================================

-- 1. BoardReaction 삭제
TRUNCATE TABLE board_reaction;

-- 2. Comment 삭제
TRUNCATE TABLE comment;

-- 3. Board 삭제
TRUNCATE TABLE board;

-- 4. Users 삭제
TRUNCATE TABLE users;

-- 5. 기타 테이블들도 삭제 (필요시)
-- TRUNCATE TABLE board_view_log;
-- TRUNCATE TABLE comment_reaction;
-- TRUNCATE TABLE file;
-- TRUNCATE TABLE notifications;
-- TRUNCATE TABLE report;
-- TRUNCATE TABLE user_sanctions;
-- TRUNCATE TABLE board_popularity_snapshot;
-- TRUNCATE TABLE dailystatistics;
-- 등등...

-- 외래키 체크 재활성화
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 확인 쿼리
-- ============================================
-- SELECT COUNT(*) FROM users;      -- 0
-- SELECT COUNT(*) FROM board;       -- 0
-- SELECT COUNT(*) FROM comment;     -- 0
-- SELECT COUNT(*) FROM board_reaction; -- 0

