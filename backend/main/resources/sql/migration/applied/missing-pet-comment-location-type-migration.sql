-- MissingPetComment 엔티티의 latitude, longitude 컬럼 타입 변경
-- Double -> DECIMAL(15,12)로 변경하여 MissingPetBoard와 일관성 유지

-- 1. 기존 데이터 백업 (선택사항)
-- CREATE TABLE MissingPetComment_backup AS SELECT * FROM MissingPetComment;

-- 2. 기존 데이터 확인
-- SELECT latitude, longitude FROM MissingPetComment WHERE latitude IS NOT NULL LIMIT 10;

-- 3. 컬럼 타입 변경
-- MySQL의 경우 ALTER TABLE로 직접 변경 가능
-- 기존 데이터는 자동으로 변환됨 (Double -> DECIMAL)

ALTER TABLE MissingPetComment 
MODIFY COLUMN latitude DECIMAL(15,12) NULL,
MODIFY COLUMN longitude DECIMAL(15,12) NULL;

-- 4. 변경 후 데이터 확인
-- SELECT latitude, longitude FROM MissingPetComment WHERE latitude IS NOT NULL LIMIT 10;

-- 5. 인덱스 확인 (필요시)
-- SHOW INDEX FROM MissingPetComment;

-- 참고:
-- - DECIMAL(15,12)는 총 15자리 중 소수점 이하 12자리까지 저장 가능
-- - 위도/경도는 일반적으로 -90 ~ 90, -180 ~ 180 범위
-- - 예: 37.5665 -> 37.566500000000 (12자리 소수점)
-- - 정밀도 손실 없이 저장 가능

