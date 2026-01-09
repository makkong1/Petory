-- LocationService 테이블에 Soft Delete 컬럼 추가
-- 이 SQL을 MySQL에서 직접 실행하세요

-- is_deleted 컬럼 추가
ALTER TABLE locationservice 
ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;

-- deleted_at 컬럼 추가
ALTER TABLE locationservice 
ADD COLUMN deleted_at DATETIME NULL;

-- 인덱스 추가 (성능 최적화)
CREATE INDEX idx_locationservice_is_deleted 
ON locationservice(is_deleted);

-- 확인
DESCRIBE locationservice;
