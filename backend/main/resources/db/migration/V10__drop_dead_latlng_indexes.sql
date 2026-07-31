-- 지도 반경검색 설계 통일 (2026-07-31): 공간 인덱스로 대체된 lat/lng B-tree 인덱스를 제거한다.
--
-- 배경
--   meetup·missing_pet_board 은 원래 (latitude, longitude) B-tree 로 바운딩 박스를 걸렀다.
--   B-tree 는 1차원 정렬이라 위도 범위 뒤의 경도가 연속 구간이 되지 못해 탐색에 못 쓰였고
--   (carerequest V4 와 같은 병리), 두 도메인 모두 geo_point + SPATIAL 인덱스로 전환했다.
--     - meetup            : V1 baseline 에 SPATIAL 존재
--     - missing_pet_board : V5 에서 전환 (커밋 f010dfc6)
--   전환 이후 이 두 B-tree 를 지우지 않아 INSERT/UPDATE 비용과 공간만 쓰고 있었다.
--
-- 삭제 근거 (지우기 전에 확인한 것)
--   1) 코드에 latitude/longitude 를 범위 조건으로 쓰는 쿼리가 남아 있지 않다.
--      현재 남은 사용처는 `latitude IS NOT NULL` 뿐이고, IS NOT NULL 은 이 인덱스가 필요 없다.
--   2) performance_schema.table_io_waits_summary_by_index_usage 기준
--      서버 가동 17일(uptime 1,466,149초) 동안 두 인덱스의 COUNT_READ 가 0 이다.
--      같은 테이블의 다른 인덱스는 수천~수십만 회라 계측 자체는 유효하다
--      (missing_pet_board.idx_missing_pet_status = 6,002회 / FK 인덱스 = 3,000회).
--   3) 두 인덱스 모두 FK 를 받치지 않는다. 삭제해도 제약이 깨지지 않는다.
--
--   ※ 계측 구간이 운영 트래픽이 아니라 로컬 개발·부하 테스트라는 한계는 있다.
--     그래서 "안 읽혔다" 하나로 지우지 않고, 위 (1) 코드 근거를 같이 확인했다.
--
-- 되돌리려면 (필요 시)
--   CREATE INDEX idx_meetup_location ON meetup (latitude, longitude);
--   CREATE INDEX idx_missing_pet_location ON missing_pet_board (latitude, longitude);

ALTER TABLE meetup
    DROP INDEX idx_meetup_location;

ALTER TABLE missing_pet_board
    DROP INDEX idx_missing_pet_location;
