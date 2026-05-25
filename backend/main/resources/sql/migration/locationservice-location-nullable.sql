-- location 컬럼을 NULL 허용으로 변경
-- MySQL은 공간 인덱스가 있으면 SRID 변경으로 해석해 ALTER를 거부.
-- 인덱스를 먼저 제거하고, SRID 4326을 명시한 채 NULL 허용으로 변경한 뒤 재생성.
ALTER TABLE locationservice DROP INDEX idx_locationservice_location_spatial;
ALTER TABLE locationservice MODIFY COLUMN location POINT NULL SRID 4326;
ALTER TABLE locationservice ADD SPATIAL INDEX idx_locationservice_location_spatial (location);
