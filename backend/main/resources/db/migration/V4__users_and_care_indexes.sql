-- 처방 5·6: admin 사용자 목록과 care 목록/주변검색의 풀스캔을 없앤다.
-- 근거: docs/analysis/query-audit/admin-2026-07-14.md §3, care-2026-07-14.md §3·§4

-- ── 처방 5: users ────────────────────────────────────────────────────────────
-- admin 사용자 목록(ORDER BY created_at DESC LIMIT 20)이 users 1만 행을 전부 읽고
-- 전부 정렬(filesort)했다. users 의 기존 인덱스는 PRIMARY·id·email·username·nickname 뿐이고
-- 전부 단일 컬럼 유니크라, 정렬에 쓸 수 있는 인덱스가 하나도 없었다.
ALTER TABLE users
    ADD INDEX idx_users_created_at (created_at);

-- ── 처방 6: carerequest ──────────────────────────────────────────────────────
-- carerequest 의 인덱스는 PRIMARY·user_idx·pet_idx 3개가 전부였고 셋 다 PK/FK 다.
-- 조회를 위해 만든 인덱스가 하나도 없어서 목록도 주변검색도 풀스캔이었다.
-- board 는 같은 목적의 인덱스를 이미 갖고 있다(idx_board_deleted_created 등).

-- 목록: WHERE is_deleted = 0 ORDER BY created_at DESC
ALTER TABLE carerequest
    ADD INDEX idx_carerequest_deleted_created (is_deleted, created_at);

-- 상태 필터 목록: WHERE status = ? AND is_deleted = 0 ORDER BY created_at DESC
ALTER TABLE carerequest
    ADD INDEX idx_carerequest_status_deleted_created (status, is_deleted, created_at);

-- ── 처방 6: 주변 검색 SPATIAL 인덱스 ────────────────────────────────────────
-- B-tree 복합 인덱스(is_deleted, latitude, longitude)는 소용이 없다 — 실측으로 확인했다.
--   · is_deleted 는 전 행이 동일해 선택도가 0 이다.
--   · longitude 는 latitude 범위 조건 뒤에 오면 범위 조건으로 쓰이지 못한다(선행 범위 컬럼 1개 제한).
--   → 옵티마이저가 계속 풀스캔을 골랐다 (3,000행).
--
-- meetup 과 locationservice 가 이미 쓰는 방식을 그대로 따른다:
--   POINT 컬럼 + SPATIAL 인덱스 + BEFORE INSERT/UPDATE 트리거로 lat/lng 에서 자동 채움.
-- 트리거로 채우므로 엔티티에 필드를 추가하지 않아도 되고, ddl-auto=validate 도 통과한다.
-- 좌표 순서는 meetup 과 동일하게 POINT(위도 경도) 를 쓴다 (기존 ST_Within 쿼리와 짝이 맞아야 한다).

-- SPATIAL 인덱스는 NOT NULL 컬럼에만 걸 수 있다. 다만 기존 행이 있는 테이블에 곧바로
-- NOT NULL POINT 컬럼을 추가하면 기존 행이 NULL 이 되어 실패한다(Error 1138).
-- 그래서 NULL 허용으로 추가 → 값을 채움 → NOT NULL 로 변경 → SPATIAL 인덱스 순서로 간다.
ALTER TABLE carerequest
    ADD COLUMN geo_point POINT SRID 4326 NULL;

UPDATE carerequest
SET geo_point = IF(latitude IS NOT NULL AND longitude IS NOT NULL,
                   ST_GeomFromText(CONCAT('POINT(', latitude, ' ', longitude, ')'), 4326),
                   ST_GeomFromText('POINT(0 0)', 4326));

ALTER TABLE carerequest
    MODIFY COLUMN geo_point POINT NOT NULL SRID 4326;

ALTER TABLE carerequest
    ADD SPATIAL INDEX idx_carerequest_geo_point_spatial (geo_point);

CREATE TRIGGER trg_carerequest_set_geo_point_insert BEFORE INSERT ON carerequest
FOR EACH ROW SET NEW.geo_point = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);

CREATE TRIGGER trg_carerequest_set_geo_point_update BEFORE UPDATE ON carerequest
FOR EACH ROW SET NEW.geo_point = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
);
