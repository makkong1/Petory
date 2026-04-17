-- locationservice 인덱스 최적화
-- 1. 중복 인덱스 제거
-- idx_lat_lng: spatial index(idx_locationservice_location_spatial)와 중복, 사용 쿼리 없음
ALTER TABLE locationservice DROP INDEX idx_lat_lng;

-- idx_address_detail: idx_name_address(name, address)가 이미 address 커버
ALTER TABLE locationservice DROP INDEX idx_address_detail;

-- 2. road_name 인덱스 추가 (findByRoadName 풀스캔 제거)
CREATE INDEX idx_road_name_deleted_rating
    ON locationservice (road_name, is_deleted, rating DESC);
