-- ============================================
-- Board 테이블에 updated_at 컬럼 추가
-- ============================================
-- 
-- 문제: Board 엔티티가 BaseTimeEntity를 상속받아 updated_at 컬럼이 필요하지만
--       실제 DB 테이블에는 이 컬럼이 없어서 에러 발생
--
-- 해결: board 테이블에 updated_at 컬럼 추가
-- ============================================

-- board 테이블에 updated_at 컬럼 추가
ALTER TABLE board 
ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
AFTER created_at;

-- 기존 데이터의 updated_at을 created_at과 동일하게 설정 (NULL이 아닌 경우)
UPDATE board 
SET updated_at = created_at 
WHERE updated_at IS NULL AND created_at IS NOT NULL;

-- 컬럼을 NOT NULL로 변경
ALTER TABLE board 
MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

