-- meetup 위치 검색용 POINT 컬럼과 공간 인덱스 추가.
-- 기존 location 컬럼은 장소 주소 문자열이므로 공간 컬럼명은 geo_point를 사용한다.
-- MySQL SRID 4326 POINT는 기존 locationservice 규약과 맞춰 POINT(latitude longitude) 순서로 저장한다.

ALTER TABLE meetup
  ADD COLUMN geo_point POINT SRID 4326 NULL;

UPDATE meetup
SET geo_point = IF(
    latitude IS NOT NULL AND longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', latitude, ' ', longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);

ALTER TABLE meetup
  MODIFY COLUMN geo_point POINT SRID 4326 NOT NULL;

ALTER TABLE meetup
  ADD SPATIAL INDEX idx_meetup_geo_point_spatial (geo_point);

CREATE TRIGGER trg_meetup_set_geo_point_insert
BEFORE INSERT ON meetup
FOR EACH ROW
SET NEW.geo_point = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);

CREATE TRIGGER trg_meetup_set_geo_point_update
BEFORE UPDATE ON meetup
FOR EACH ROW
SET NEW.geo_point = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);
