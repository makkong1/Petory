-- Users 테이블에 소프트 삭제 컬럼 추가
ALTER TABLE users 
ADD COLUMN is_deleted TINYINT(1) DEFAULT 0 AFTER suspended_until,
ADD COLUMN deleted_at DATETIME NULL AFTER is_deleted;

-- 기존 데이터는 삭제되지 않은 상태로 설정
UPDATE users SET is_deleted = 0 WHERE is_deleted IS NULL;

