-- LocationService 테이블에 Soft Delete 컬럼 추가
-- is_deleted와 deleted_at 컬럼 추가

-- ============================================
-- Soft Delete 컬럼 추가
-- ============================================

-- is_deleted 컬럼 추가 (이미 존재할 수 있음)
-- 컬럼이 이미 존재하면 에러 발생 (무시하고 다음 실행)
ALTER TABLE locationservice 
ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;

-- deleted_at 컬럼 추가 (이미 존재할 수 있음)
-- 컬럼이 이미 존재하면 에러 발생 (무시하고 다음 실행)
ALTER TABLE locationservice 
ADD COLUMN deleted_at DATETIME NULL;

-- ============================================
-- MySQL 8.0.23+ 버전을 사용하는 경우 (IF NOT EXISTS 지원)
-- ============================================
-- ALTER TABLE locationservice 
-- ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;
-- 
-- ALTER TABLE locationservice 
-- ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL;

-- ============================================
-- 인덱스 추가 (성능 최적화)
-- ============================================
-- is_deleted 필터링이 자주 사용되므로 인덱스 추가 권장
CREATE INDEX idx_locationservice_is_deleted 
ON locationservice(is_deleted);

-- MySQL 5.7.4+ 버전을 사용하는 경우 (IF NOT EXISTS 지원):
-- CREATE INDEX IF NOT EXISTS idx_locationservice_is_deleted 
-- ON locationservice(is_deleted);

-- ============================================
-- 확인 쿼리
-- ============================================
-- DESCRIBE locationservice;
-- SHOW COLUMNS FROM locationservice;
-- SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'locationservice'
--   AND COLUMN_NAME IN ('is_deleted', 'deleted_at');
