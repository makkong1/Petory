-- locationservice 좌표 수정 시 POINT 공간 컬럼도 함께 갱신한다.
-- 기존 INSERT 트리거와 동일하게 POINT(latitude longitude), SRID 4326 규약을 따른다.

CREATE TRIGGER trg_locationservice_set_location_update
BEFORE UPDATE ON locationservice
FOR EACH ROW
SET NEW.location = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);
