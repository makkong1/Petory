-- ============================================================================
-- Meetup 쿼리 EXPLAIN 실행 계획 확인용 SQL
-- ============================================================================

-- 3단계 쿼리 (Bounding Box + B+Tree 복합 인덱스) - 인덱스 사용 성공
EXPLAIN SELECT m.* FROM meetup m
WHERE m.date > '2026-02-07 19:20:16'
  AND (m.status IS NULL OR m.status != 'COMPLETED') 
  AND (m.is_deleted = false OR m.is_deleted IS NULL) 
  AND m.latitude BETWEEN (37.5665 - 5.0 / 111.0) AND (37.5665 + 5.0 / 111.0)
  AND m.longitude BETWEEN (126.9780 - 5.0 / (111.0 * cos(radians(37.5665)))) 
                      AND (126.9780 + 5.0 / (111.0 * cos(radians(37.5665))))
  AND (6371 * acos(cos(radians(37.5665)) * cos(radians(m.latitude)) * 
       cos(radians(m.longitude) - radians(126.9780)) + 
       sin(radians(37.5665)) * sin(radians(m.latitude)))) <= 5.0 
ORDER BY (6371 * acos(cos(radians(37.5665)) * cos(radians(m.latitude)) * 
         cos(radians(m.longitude) - radians(126.9780)) + 
         sin(radians(37.5665)) * sin(radians(m.latitude)))) ASC, m.date ASC;
-- 결과: type=range, key=idx_meetup_location, rows=117, filtered=0.60%

-- ============================================================================
-- 4단계 (현행) 쿼리 (공간 인덱스 R-Tree) - SpringDataJpaMeetupRepository.findNearbyMeetupIds
-- ============================================================================
EXPLAIN
SELECT m.idx FROM meetup m
WHERE m.date > NOW()
  AND (m.status IS NULL OR m.status NOT IN ('COMPLETED', 'CANCELLED'))
  AND (m.is_deleted = false OR m.is_deleted IS NULL)
  AND m.latitude IS NOT NULL
  AND m.longitude IS NOT NULL
  AND ST_Within(m.geo_point, ST_GeomFromText(
        CONCAT('POLYGON((',
          37.5665 - (5.0 / 111.0), ' ', 126.9780 - (5.0 / (111.0 * COS(RADIANS(37.5665)))), ', ',
          37.5665 - (5.0 / 111.0), ' ', 126.9780 + (5.0 / (111.0 * COS(RADIANS(37.5665)))), ', ',
          37.5665 + (5.0 / 111.0), ' ', 126.9780 + (5.0 / (111.0 * COS(RADIANS(37.5665)))), ', ',
          37.5665 + (5.0 / 111.0), ' ', 126.9780 - (5.0 / (111.0 * COS(RADIANS(37.5665)))), ', ',
          37.5665 - (5.0 / 111.0), ' ', 126.9780 - (5.0 / (111.0 * COS(RADIANS(37.5665)))), '))'),
        4326))
  AND ST_Distance_Sphere(m.geo_point, ST_GeomFromText(
        CONCAT('POINT(', 37.5665, ' ', 126.9780, ')'), 4326)) <= (5.0 * 1000)
ORDER BY ST_Distance_Sphere(m.geo_point, ST_GeomFromText(
        CONCAT('POINT(', 37.5665, ' ', 126.9780, ')'), 4326)) ASC, m.date ASC
LIMIT 500;
-- 실측(현재 데이터): 옵티마이저가 공간 인덱스 대신 idx_meetup_date(date range)를 선택,
--   ST_Within/ST_Distance_Sphere는 post-filter로 평가됨.
--   → date 조건이 소량 데이터에서 이미 극도로 선택적(rows=1)이라 비용상 date 인덱스가 이김.
--   → 데이터·미래 날짜 모임이 늘면 date 선택도가 떨어져 공간 인덱스로 전환될 여지가 생김.
-- 상세: EXPLAIN FORMAT=JSON ... / 실측: EXPLAIN ANALYZE ...
-- 공간 경로 강제 비교: ... FROM meetup m FORCE INDEX (idx_meetup_geo_point_spatial) ...

-- ============================================================================
-- 확인 포인트
-- ============================================================================
-- 3단계 key: idx_meetup_location (B+Tree, 위도 1축만 가지치기)
-- 4단계 인덱스 후보: idx_meetup_geo_point_spatial (R-Tree, 위도·경도 2축 동시 가지치기)
--   단, 현재 데이터에서는 옵티마이저가 idx_meetup_date를 선택(비용 기반 정상 판단)
-- type: range (범위 스캔)
-- Extra: Using index condition (인덱스 조건 푸시다운)
