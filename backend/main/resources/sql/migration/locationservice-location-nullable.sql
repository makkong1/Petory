-- BEFORE INSERT 트리거: Hibernate가 location 컬럼을 INSERT에서 빠뜨리는 문제 해결.
-- MySQL SRID 4326은 POINT(latitude longitude) 순서 (기존 findByRadius 쿼리와 동일 규약).
--
-- 선행 작업: 이전 실패한 마이그레이션에서 공간 인덱스가 DROP된 상태이므로 먼저 재생성.
ALTER TABLE locationservice ADD SPATIAL INDEX idx_locationservice_location_spatial (location);

CREATE TRIGGER trg_locationservice_set_location
BEFORE INSERT ON locationservice
FOR EACH ROW
SET NEW.location = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);
