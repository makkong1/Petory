-- ============================================================================
-- Meetup 쿼리 EXPLAIN 실행 계획 확인용 SQL
-- ============================================================================

-- 현재 적용된 쿼리 (Bounding Box 방식) - 인덱스 사용 성공
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
-- 확인 포인트
-- ============================================================================
-- key: idx_meetup_location (인덱스 사용)
-- type: range (범위 스캔)
-- rows: 117 (스캔 행 수)
-- Extra: Using index condition (인덱스 조건 푸시다운)
