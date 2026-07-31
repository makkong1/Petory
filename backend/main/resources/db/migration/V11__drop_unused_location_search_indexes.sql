-- 지도 반경검색 설계 통일 (2026-07-31): 읽는 쿼리가 없는 장소 검색 인덱스를 제거한다.
--
-- 배경
--   locationservice 의 읍면동·도로명 검색 전용 인덱스다. 그런데 그 검색 메서드는
--   "프론트 미사용으로 비활성화" 라는 주석과 함께 3계층에 전부 주석 처리돼 있었다.
--     - SpringDataJpaLocationServiceRepository (findByEupmyeondong / findByRoadName)
--     - LocationServiceRepository (인터페이스 선언)
--     - JpaLocationServiceAdapter (구현)
--   즉 인덱스를 USE INDEX 로 지정하던 쿼리 자체가 없는데 인덱스만 남아
--   INSERT/UPDATE 비용과 공간을 쓰고 있었다. 이번에 주석 코드를 지우면서 같이 정리한다.
--
-- 삭제 근거
--   1) 코드에 두 인덱스를 참조하는 쿼리가 없다 (주석까지 제거해 확실히 없다).
--   2) performance_schema.table_io_waits_summary_by_index_usage 기준 COUNT_READ = 0.
--      같은 테이블의 idx_locationservice_deleted_rating 은 687,437회,
--      idx_locationservice_sigungu_deleted_rating 은 24,162회라 계측 자체는 유효하다.
--   3) FK 를 받치지 않는다.
--
--   ※ 계측 구간이 운영 트래픽이 아니라 로컬 개발·부하 테스트 17일치라는 한계는 있다.
--     그래서 "안 읽혔다" 하나가 아니라 (1) 코드에 호출부가 없다는 것을 주 근거로 삼았다.
--
-- 되돌리려면 (읍면동·도로명 검색을 다시 열 때)
--   CREATE INDEX idx_locationservice_eupmyeondong_deleted_rating
--     ON locationservice (eupmyeondong, is_deleted, rating);
--   CREATE INDEX idx_road_name_deleted_rating
--     ON locationservice (road_name, is_deleted, rating);

ALTER TABLE locationservice
    DROP INDEX idx_locationservice_eupmyeondong_deleted_rating,
    DROP INDEX idx_road_name_deleted_rating;
