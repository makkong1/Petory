-- ── missing_pet_board 주변 검색 SPATIAL 인덱스 ──────────────────────────────
-- carerequest(V4)와 완전히 같은 병리다. B-tree 인덱스 idx_missing_pet_location(latitude,
-- longitude)가 있는데도 findHomeCandidatesInBoundingBox 가 풀스캔한다 — 실측으로 확인했다.
--   · latitude BETWEEN 뒤의 longitude BETWEEN 은 범위 조건으로 쓰이지 못한다(선행 범위 컬럼 1개 제한).
--   → 옵티마이저가 Table scan(3,000행)을 골라 42건을 찾으려 전 행을 훑고 정렬한다.
--
-- meetup · locationservice · carerequest 가 이미 쓰는 방식을 그대로 따른다:
--   POINT 컬럼 + SPATIAL 인덱스 + BEFORE INSERT/UPDATE 트리거로 lat/lng 에서 자동 채움.
-- 트리거로 채우므로 엔티티에 필드를 추가하지 않아도 되고, ddl-auto=validate 도 통과한다.
-- 좌표 순서는 POINT(위도 경도) 를 쓴다(기존 도메인들의 ST_Within 쿼리와 짝이 맞아야 한다).

-- SPATIAL 인덱스는 NOT NULL 컬럼에만 걸 수 있다. 기존 행이 있는 테이블에 곧바로 NOT NULL
-- POINT 컬럼을 추가하면 실패하므로(Error 1138), NULL 허용 추가 → 값 채움 → NOT NULL → 인덱스 순서로 간다.
ALTER TABLE missing_pet_board
    ADD COLUMN geo_point POINT SRID 4326 NULL;

UPDATE missing_pet_board
SET geo_point = IF(latitude IS NOT NULL AND longitude IS NOT NULL,
                   ST_GeomFromText(CONCAT('POINT(', latitude, ' ', longitude, ')'), 4326),
                   ST_GeomFromText('POINT(0 0)', 4326));

ALTER TABLE missing_pet_board
    MODIFY COLUMN geo_point POINT NOT NULL SRID 4326;

ALTER TABLE missing_pet_board
    ADD SPATIAL INDEX idx_missing_pet_geo_point_spatial (geo_point);

CREATE TRIGGER trg_missing_pet_set_geo_point_insert BEFORE INSERT ON missing_pet_board
FOR EACH ROW SET NEW.geo_point = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);

CREATE TRIGGER trg_missing_pet_set_geo_point_update BEFORE UPDATE ON missing_pet_board
FOR EACH ROW SET NEW.geo_point = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);
