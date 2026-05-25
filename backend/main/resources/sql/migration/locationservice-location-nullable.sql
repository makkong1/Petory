-- location 컬럼 NULL화는 MySQL 공간 인덱스 제약(NOT NULL 필수)으로 불가.
-- 대신 BEFORE INSERT 트리거로 lat/lng → POINT 자동 세팅.
--
-- 선행 작업: 이전 실패한 마이그레이션에서 공간 인덱스가 DROP된 상태이므로 먼저 재생성.
ALTER TABLE locationservice ADD SPATIAL INDEX idx_locationservice_location_spatial (location);

-- BEFORE INSERT 트리거: Hibernate가 location 컬럼을 INSERT에서 빠뜨려도
-- lat/lng에서 자동으로 POINT(SRID 4326)를 채운다.
-- lat/lng 없는 row는 FacilitySyncService.isValid()에서 이미 걸러지나,
-- 혹시 도달하면 POINT(0 0)으로 fallback해 NOT NULL 위반 방지.
CREATE TRIGGER trg_locationservice_set_location
BEFORE INSERT ON locationservice
FOR EACH ROW
SET NEW.location = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.longitude, ' ', NEW.latitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);
