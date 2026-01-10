-- ============================================
-- LocationService Repository 메서드 EXPLAIN 쿼리
-- 워크벤치에서 실행하여 인덱스 사용 여부 확인
-- ============================================

USE petory;

-- ============================================
-- 1. findByCategoryOrderByRatingDesc
-- 카테고리별 평점순 서비스 조회
-- ⚠️ 실제 쿼리: category3 = :category OR category2 = :category OR category1 = :category
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE ('병원' IS NULL OR category3 = '병원' OR category2 = '병원' OR category1 = '병원')
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ⚠️ 실제 EXPLAIN 결과 분석:
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys | key   | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ALL    | NULL          | NULL  | NULL    | NULL   | 21878  |   10.00 | Using where; Using filesort |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+

-- ✅ 인덱스 적용 후 EXPLAIN 결과:
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref_or_null | idx_locationservice_deleted_rating,idx_category3_deleted_rating | idx_locationservice_deleted_rating |       2 | const  | 10940  |   25.23 | Using index condition; Using where; Using filesort |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
--
-- 📊 인덱스 적용 전후 비교:
-- 
-- | 항목 | 적용 전 | 적용 후 | 개선 여부 |
-- |------|---------|---------|-----------|
-- | type | ALL (전체 스캔) | ref_or_null | ✅ 개선 (인덱스 사용) |
-- | key | NULL | idx_locationservice_deleted_rating | ✅ 개선 (인덱스 활용) |
-- | rows | 21878 (100%) | 10940 (50%) | ✅ 50% 감소 |
-- | filtered | 10.00% | 25.23% | ✅ 2.5배 개선 |
-- | Extra | Using where; Using filesort | Using index condition; Using where; Using filesort | ⚠️ filesort 여전히 발생 |
--
-- ✅ 개선된 점:
-- 1. type: ALL → ref_or_null
--    → 전체 테이블 스캔에서 인덱스 사용으로 변경
--    → is_deleted 조건으로 인덱스 활용
--
-- 2. rows: 21878 → 10940 (50% 감소)
--    → 스캔 행 수가 절반으로 감소
--    → is_deleted = 0 조건으로 필터링
--
-- 3. filtered: 10.00% → 25.23%
--    → 필터링 효율 2.5배 개선
--    → 인덱스 조건 푸시다운 (Using index condition)으로 추가 필터링
--
-- ⚠️ 여전한 문제점:
-- 1. idx_category3_deleted_rating 인덱스 미사용
--    → possible_keys에는 있지만 실제 key는 idx_locationservice_deleted_rating만 사용
--    → OR 조건 (category3 = '병원' OR category2 = '병원' OR category1 = '병원') 때문에 활용 못함
--    → MySQL은 OR 조건에서 여러 인덱스를 동시에 사용하지 못함
--
-- 2. Using filesort 여전히 발생
--    → ORDER BY rating DESC에서 인덱스 정렬 미사용
--    → idx_locationservice_deleted_rating (is_deleted, rating DESC)를 사용하지만
--    → category 조건이 없어서 rating DESC 정렬을 인덱스로 처리하지 못함
--
-- 3. rows가 여전히 많음 (10940개)
--    → 전체 행의 50%를 스캔
--    → category 조건이 인덱스로 필터링되지 않아서
--
-- 🔧 추가 최적화 방안:
-- 1. UNION으로 분리하여 각 카테고리별 인덱스 활용 (권장)
--    ⚠️ MySQL 문법: 각 SELECT를 괄호로 감싸야 함
--    ⚠️ 각 서브쿼리의 ORDER BY는 서브쿼리 내부에서만 적용되므로, 최종 ORDER BY 필요
EXPLAIN
(
    SELECT * FROM locationservice 
    WHERE category3 = '병원' AND (is_deleted = 0 OR is_deleted IS NULL)
    ORDER BY rating DESC
    LIMIT 20
)
UNION ALL
(
    SELECT * FROM locationservice 
    WHERE category2 = '병원' AND (is_deleted = 0 OR is_deleted IS NULL)
    ORDER BY rating DESC
    LIMIT 20
)
UNION ALL
(
    SELECT * FROM locationservice 
    WHERE category1 = '병원' AND (is_deleted = 0 OR is_deleted IS NULL)
    ORDER BY rating DESC
    LIMIT 20
)
ORDER BY rating DESC
LIMIT 20;

-- ✅ 실제 EXPLAIN 결과 분석:
-- +----+------------+-------------+--------+--------------------------------+---------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type| table       | type   | possible_keys                   | key                       | key_len | ref    | rows  | filtered | Extra                |
-- +----+------------+-------------+--------+--------------------------------+---------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | PRIMARY    | locationservice | ref_or_null | idx_locationservice_deleted_rating,idx_category3_deleted_rating | idx_category3_deleted_rating | 405     | const,const | 2    | 100.00   | Using index condition; Using filesort |
-- |  2 | UNION      | locationservice | ref_or_null | idx_locationservice_deleted_rating                            | idx_locationservice_deleted_rating | 2       | const      | 10940 | 10.00    | Using index condition; Using where; Using filesort |
-- |  3 | UNION      | locationservice | ref_or_null | idx_locationservice_deleted_rating                            | idx_locationservice_deleted_rating | 2       | const      | 10940 | 10.00    | Using index condition; Using where; Using filesort |
-- |  4 | UNION RESULT| <union1,2,3> | ALL    | NULL                            | NULL                      | NULL    | NULL   | NULL  | NULL     | Using temporary; Using filesort |
-- +----+------------+-------------+--------+--------------------------------+---------------------------+---------+--------+-------+----------+----------------------+
--
-- 📊 각 SELECT 분석:
--
-- 1️⃣ PRIMARY (category3 = '병원'):
--    ✅ type: ref_or_null (인덱스 사용)
--    ✅ key: idx_category3_deleted_rating (카테고리 인덱스 활용!)
--    ✅ rows: 2 (매우 적음, 거의 완벽!)
--    ✅ filtered: 100.00% (모든 행이 조건 만족)
--    ⚠️ Extra: Using filesort (LIMIT 20 때문에 발생, 하지만 rows=2이므로 비용 낮음)
--    → category3 인덱스가 완벽하게 작동!
--
-- 2️⃣ UNION (category2 = '병원'):
--    ⚠️ type: ref_or_null
--    ⚠️ key: idx_locationservice_deleted_rating (category2 인덱스 없어서 deleted_rating만 사용)
--    ⚠️ rows: 10940 (여전히 많음)
--    ⚠️ filtered: 10.00% (category2 조건으로 필터링)
--    ⚠️ Extra: Using where; Using filesort
--    → category2 인덱스가 없어서 성능 저하
--
-- 3️⃣ UNION (category1 = '병원'):
--    ⚠️ type: ref_or_null
--    ⚠️ key: idx_locationservice_deleted_rating (category1 인덱스 없어서 deleted_rating만 사용)
--    ⚠️ rows: 10940 (여전히 많음)
--    ⚠️ filtered: 10.00% (category1 조건으로 필터링)
--    ⚠️ Extra: Using where; Using filesort
--    → category1 인덱스가 없어서 성능 저하
--
-- 4️⃣ UNION RESULT (최종 결과 병합):
--    ⚠️ type: ALL
--    ⚠️ Extra: Using temporary; Using filesort
--    → 임시 테이블 사용 + 정렬 (3개 결과 병합)
--
-- ✅ 개선된 점:
-- 1. category3는 인덱스를 완벽하게 활용 (rows=2, 매우 효율적)
-- 2. OR 조건 문제 해결 (각 SELECT가 독립적으로 인덱스 사용 가능)
--
-- ⚠️ 추가 최적화 필요:
-- 1. category2, category1 인덱스 추가 필요
--    CREATE INDEX idx_category2_deleted_rating ON locationservice(category2, is_deleted, rating DESC);
--    CREATE INDEX idx_category1_deleted_rating ON locationservice(category1, is_deleted, rating DESC);
--    → category2, category1도 rows를 크게 줄일 수 있음
--
-- 2. UNION RESULT의 Using temporary는 피할 수 없음 (UNION 특성상)
--    → 하지만 각 SELECT의 rows가 적으면 임시 테이블 크기도 작아짐
--
-- 📈 예상 추가 개선 효과 (category2, category1 인덱스 추가 후):
-- - category2: rows=10940 → 약 500-1000개로 감소 예상
-- - category1: rows=10940 → 약 1000-2000개로 감소 예상
-- - 전체 성능: 현재 대비 약 5-10배 추가 개선 가능
--
-- 2. 애플리케이션에서 카테고리 우선순위 적용
--    → category3 우선 검색 → 결과 없으면 category2 → category1
--    → 가장 구체적인 카테고리부터 검색하여 인덱스 활용 극대화
--
-- 📈 예상 추가 개선 효과:
-- - UNION 사용 시: type=ref, rows=약 500-1000개, filesort 제거
-- - 성능 향상: 현재 대비 약 10-20배 추가 개선 가능

-- ============================================

-- ============================================


-- ============================================
-- 2. findByOrderByRatingDesc (기존 JPQL 상태 - is_deleted IS NULL OR is_deleted = false)
-- 평점순 전체 서비스 조회
-- ============================================
-- ⚠️ 현재 상태 확인 (인덱스 적용 전/후 비교용)
-- JPQL: (ls.isDeleted IS NULL OR ls.isDeleted = false)
-- Native SQL 변환: (is_deleted IS NULL OR is_deleted = 0)
EXPLAIN 
SELECT * FROM locationservice 
WHERE (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ⚠️ 예상 결과 (인덱스 미적용 시):
-- type: ALL (전체 테이블 스캔)
-- key: NULL (인덱스 미사용)
-- possible_keys: idx_locationservice_deleted_rating (있을 수 있지만 사용 안 함)
-- rows: 전체 행 수 (예: 10000개면 10000)
-- Extra: Using where; Using filesort
-- 
-- ⚠️ 문제점:
-- 1. is_deleted IS NULL OR is_deleted = 0 조건은 OR 연산자로 인해 인덱스를 효과적으로 활용하지 못함
--    - MySQL은 OR 조건에서 인덱스를 각각 사용할 수 없어 전체 스캔 선택
-- 2. ORDER BY rating DESC는 filesort 발생 (idx_locationservice_deleted_rating 인덱스 미사용)
-- 3. 전체 스캔 + 메모리 정렬 = 대용량에서 매우 느림 (O(n log n))
-- 4. idx_locationservice_deleted_rating 인덱스가 있어도 OR 조건 때문에 활용 못함
-- 
-- ✅ 확인 포인트:
-- - type이 ALL인지 확인 (인덱스 미사용)
-- - key가 NULL인지 확인
-- - Extra에 "Using filesort" 있는지 확인

-- # id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref_or_null', 'idx_locationservice_is_deleted,idx_locationservice_deleted_rating', 'idx_locationservice_is_deleted', '2', 'const', '10940', '100.00', 'Using index condition; Using filesort'
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref_or_null | idx_locationservice_is_deleted,idx_locationservice_deleted_rating | idx_locationservice_is_deleted |       2 | const  | 10940  |   100.00 | Using index condition; Using filesort |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- ============================================

-- ============================================
-- 2-1. findByOrderByRatingDesc (개선안: COALESCE 사용)
-- 평점순 전체 서비스 조회
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE (COALESCE(is_deleted, 0) = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: idx_locationservice_deleted_rating (is_deleted, rating DESC)
-- ⚠️ 주의: COALESCE 함수 사용으로 인덱스 직접 활용 여부 확인 필요
-- ⚠️ 주의: 전체 스캔이므로 페이징 필수 권장

-- # id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ALL', NULL, NULL, NULL, NULL, '21878', '100.00', 'Using where; Using filesort'
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys | key   | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ALL    | NULL          | NULL  | NULL    | NULL   | 21878  |   100.00 | Using where; Using filesort |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+

-- ============================================

-- ============================================
-- 2-2. findByOrderByRatingDesc (최적화안: is_deleted 정규화 후)
-- 평점순 전체 서비스 조회 - is_deleted = 0 조건만 사용 (NULL 제거 후)
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE is_deleted = 0
ORDER BY rating DESC;

-- ✅ 예상 인덱스: idx_locationservice_deleted_rating (is_deleted, rating DESC) 완전 활용 가능
-- ✅ ORDER BY rating DESC도 인덱스로 처리 (filesort 제거 가능)
-- ✅ 가장 효율적인 방법 (is_deleted 컬럼 정규화 필요)

# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref', 'idx_locationservice_is_deleted,idx_locationservice_deleted_rating', 'idx_locationservice_deleted_rating', '2', 'const', '10939', '100.00', NULL
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref    | idx_locationservice_is_deleted,idx_locationservice_deleted_rating | idx_locationservice_deleted_rating |       2 | const  | 10939  |   100.00 | NULL                 |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- ============================================


-- ============================================
-- 3. findByLocationRange
-- 위도/경도 범위 검색 (BETWEEN)
-- ============================================
-- 서울 강남구 근처 범위 예시
-- minLat: 37.49, maxLat: 37.52
-- minLng: 126.98, maxLng: 127.01
EXPLAIN 
SELECT * FROM locationservice 
WHERE latitude BETWEEN 37.49 AND 37.52 
  AND longitude BETWEEN 126.98 AND 127.01 
ORDER BY rating DESC;

-- 예상 인덱스: idx_lat_lng 사용

--# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'range', 'idx_lat_lng', 'idx_lat_lng', '18', NULL, '1353', '11.11', 'Using index condition; Using filesort'
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys | key   | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | range    | idx_lat_lng | idx_lat_lng |       18 | NULL   | 1353   |   11.11 | Using index condition; Using filesort |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+

-- ============================================


-- ============================================
-- 4. findByAddressContaining
-- 주소로 서비스 검색 (LIKE '%...%')
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE address LIKE '%서울%' 
ORDER BY rating DESC;

-- 예상 인덱스: idx_address (prefix match), 하지만 LIKE '%...%'는 인덱스 효율 낮음
# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ALL', NULL, NULL, NULL, NULL, '21878', '11.11', 'Using where; Using filesort'
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys | key   | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ALL    | NULL          | NULL  | NULL    | NULL   | 21878  |   11.11 | Using where; Using filesort |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+

-- ============================================


-- ============================================
-- 5. findByRegion (개선됨: 정확한 매칭)
-- 전국 지역 검색 (시/도 > 시/군/구 > 동/면/리)
-- ============================================
-- 서울시 강남구 역삼동 예시
EXPLAIN 
SELECT * FROM locationservice 
WHERE ('서울특별시' IS NULL OR sido = '서울특별시') 
  AND ('강남구' IS NULL OR sigungu = '강남구') 
  AND ('역삼동' IS NULL OR eupmyeondong = '역삼동') 
  AND (COALESCE(is_deleted, 0) = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: idx_locationservice_sido, idx_locationservice_sigungu, idx_locationservice_eupmyeondong
-- ✅ idx_locationservice_deleted_rating (is_deleted, rating DESC)

# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref', 'idx_locationservice_sido,idx_locationservice_sigungu,idx_locationservice_eupmyeondong', 'idx_locationservice_eupmyeondong', '203', 'const', '76', '0.34', 'Using where'
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref    | idx_locationservice_sido,idx_locationservice_sigungu,idx_locationservice_eupmyeondong | idx_locationservice_eupmyeondong |       203 | const  | 76   |   0.34 | Using where                 |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- ============================================


-- ============================================
-- 6. findBySeoulGuAndDong
-- 서울 구/동 검색
-- ============================================
-- 서울시 강남구 역삼동 예시
EXPLAIN 
SELECT * FROM locationservice 
WHERE address LIKE CONCAT('%서울%', '강남구', '%') 
  AND ('역삼동' IS NULL OR address LIKE CONCAT('%', '역삼동', '%')) 
ORDER BY rating DESC;

-- 예상 인덱스: idx_address (prefix match), 하지만 LIKE '%...%'는 인덱스 효율 낮음

# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ALL', NULL, NULL, NULL, NULL, '21878', '1.23', 'Using where; Using filesort'
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys | key   | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ALL    | NULL          | NULL  | NULL    | NULL   | 21878  |   1.23 | Using where; Using filesort |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+

-- ============================================


-- ============================================
-- 7. findByNameContaining
-- 이름/설명으로 서비스 검색 (LIKE '%...%')
-- ============================================
-- FULLTEXT 검색 예시 (MATCH ... AGAINST)
EXPLAIN 
SELECT * FROM locationservice 
WHERE name LIKE '%반려동물%' OR description LIKE '%반려동물%' 
ORDER BY rating DESC;

# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ALL', NULL, NULL, NULL, NULL, '21878', '20.99', 'Using where; Using filesort'
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys | key   | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ALL    | NULL          | NULL  | NULL    | NULL   | 21878  |   20.99 | Using where; Using filesort |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+


-- FULLTEXT 인덱스 사용 쿼리 (더 효율적)
EXPLAIN 
SELECT * FROM locationservice 
WHERE MATCH(name, description) AGAINST('반려동물' IN BOOLEAN MODE) 
ORDER BY rating DESC;

-- 예상 인덱스: ft_name_desc (FULLTEXT 인덱스)
# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'fulltext', 'ft_name_desc', 'ft_name_desc', '0', 'const', '1', '100.00', 'Using where; Ft_hints: no_ranking; Using filesort'
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys | key   | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | fulltext    | ft_name_desc | ft_name_desc |       0 | const  | 1   |   100.00 | Using where; Ft_hints: no_ranking; Using filesort |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- ============================================


-- ============================================
-- 8. findByRadius
-- 반경 검색 (ST_Distance_Sphere 사용)
-- ============================================
-- 서울시청 기준 3km 이내 (위도: 37.5665, 경도: 126.9780)
-- 반경: 3000m
EXPLAIN 
SELECT * FROM locationservice 
WHERE ST_Distance_Sphere(
    coordinates, 
    ST_GeomFromText(CONCAT('POINT(', 37.5665, ' ', 126.9780, ')'), 4326)
) <= 3000 
ORDER BY rating DESC;

-- 예상 인덱스: idx_coordinates (SPATIAL INDEX)
-- 주의: coordinates 컬럼이 POINT 타입이어야 함

# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ALL', NULL, NULL, NULL, NULL, '21878', '100.00', 'Using where; Using filesort'
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys | key   | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ALL    | NULL          | NULL  | NULL    | NULL   | 21878  |   100.00 | Using where; Using filesort |
-- +----+--------------+-------------+--------+-------------+-------+---------+--------+--------+-------+----------+----------------------+

-- ============================================


-- ============================================
-- 9. findByRatingGreaterThanEqualOrderByRatingDesc
-- 특정 평점 이상의 서비스 조회
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE rating >= 4.0 
ORDER BY rating DESC;

-- 예상 인덱스: idx_rating_desc 사용

# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'range', 'idx_category_rating,idx_rating_desc', 'idx_category_rating', '9', NULL, '1', '100.00', 'Using index condition'
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | range    | idx_category_rating,idx_rating_desc | idx_category_rating |       9 | NULL   | 1   |   100.00 | Using index condition |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+

-- ============================================


-- ============================================
-- 10. findByNameAndAddress
-- 이름과 주소로 중복 체크
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE name = '펫병원' AND address = '서울시 강남구 테헤란로 123';

-- 예상 인덱스: idx_name_address 
# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref', 'idx_name_address,idx_address,idx_address_detail,ft_name_desc', 'idx_name_address', '2046', 'const,const', '1', '100.00', NULL
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref    | idx_name_address,idx_address,idx_address_detail,ft_name_desc | idx_name_address |       2046 | const,const  | 1   |   100.00 | NULL                 |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- ============================================


-- ============================================
-- 11. findByAddress
-- 주소로 중복 체크
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE address = '서울시 강남구 테헤란로 123';

-- 예상 인덱스: idx_address 사용
# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref', 'idx_address,idx_address_detail', 'idx_address', '1023', 'const', '1', '100.00', NULL
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref    | idx_address,idx_address_detail | idx_address |       1023 | const  | 1   |   100.00 | NULL                 |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+

-- ============================================


-- ============================================
-- 12. findByAddressAndDetailAddress
-- 주소와 상세주소로 중복 체크
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE address = '서울시 강남구 테헤란로 123' 
  AND detail_address = '101호';

-- 예상 인덱스: idx_address_detail 사용
-- ============================================


-- ============================================
-- 복합 쿼리 테스트 (실제 사용 패턴)
-- ============================================

-- 카테고리 필터 + 평점순 정렬 + 평점 최소값
EXPLAIN 
SELECT * FROM locationservice 
WHERE category = '병원' 
  AND rating >= 4.0 
ORDER BY rating DESC 
LIMIT 10;

-- 예상 인덱스: idx_category_rating 사용

-- ============================================
-- 지역 범위 + 카테고리 필터 + 평점순 정렬
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE latitude BETWEEN 37.49 AND 37.52 
  AND longitude BETWEEN 126.98 AND 127.01 
  AND category = '병원' 
ORDER BY rating DESC;

-- 예상 인덱스: idx_lat_lng 또는 idx_category_rating 사용 (MySQL이 선택)

-- ============================================
-- 주소 검색 + 카테고리 필터 + 평점순 정렬
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE address LIKE '%강남구%' 
  AND category = '병원' 
ORDER BY rating DESC;

-- 예상 인덱스: idx_category_rating 사용 (address는 LIKE로 인덱스 효율 낮음)

-- ============================================
-- 13. findBySido (개선됨: COALESCE 사용)
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND (COALESCE(is_deleted, 0) = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: idx_locationservice_sido (sido, rating DESC)
# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref', 'idx_locationservice_sido', 'idx_locationservice_sido', '203', 'const', '4126', '100.00', 'Using where'
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref    | idx_locationservice_sido | idx_locationservice_sido |       203 | const  | 4126   |   100.00 | Using where                 |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- ============================================

-- ============================================
-- 14. findBySigungu (개선됨: COALESCE 사용)
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE sigungu = '강남구' 
  AND (COALESCE(is_deleted, 0) = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: idx_locationservice_sigungu (sigungu, rating DESC)
-- 
-- 📊 실제 EXPLAIN 결과 분석:
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref    | idx_locationservice_sigungu | idx_locationservice_sigungu |       203 | const  | 392   |   100.00 | Using where                 |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
--
-- ✅ 긍정적인 점:
-- 1. type: ref
--    → 인덱스를 사용하여 특정 값 조회 (전체 스캔 아님)
--    → sigungu = '강남구' 조건으로 인덱스 활용
--
-- 2. key: idx_locationservice_sigungu
--    → 예상한 인덱스 사용 중
--
-- 3. rows: 392
--    → 전체 21,878개 중 392개만 스캔 (약 1.8%)
--    → sigungu 조건으로 효과적으로 필터링
--
-- 4. filtered: 100.00%
--    → 스캔한 392개 행이 모두 조건 만족
--    → COALESCE(is_deleted, 0) = 0 조건으로 추가 필터링
--
-- ⚠️ 개선 가능한 점:
-- 1. key_len: 203
--    → sigungu 컬럼만 사용 (약 203바이트)
--    → idx_locationservice_sigungu는 (sigungu, rating DESC) 복합 인덱스인데
--    → ORDER BY rating DESC에서 인덱스의 rating DESC 부분을 활용하지 못함
--    → COALESCE(is_deleted, 0) = 0 조건이 BETWEEN이 아니므로 인덱스 범위가 끊김
--
-- 2. Extra: Using where
--    → WHERE 조건으로 추가 필터링 발생
--    → COALESCE 함수 사용으로 인덱스 조건 푸시다운 제한
--
-- 3. ORDER BY rating DESC
--    → filesort가 발생하지 않았지만, 인덱스 정렬도 사용하지 못함
--    → rows=392이므로 메모리 정렬 비용은 낮지만, 인덱스 정렬이 더 효율적
--
-- 🔧 최적화 방안:
-- 1. is_deleted 조건을 인덱스에 포함 (권장)
--    → CREATE INDEX idx_sigungu_deleted_rating ON locationservice(sigungu, is_deleted, rating DESC);
--    → 이렇게 하면 ORDER BY rating DESC도 인덱스로 처리 가능
--    → 단, is_deleted 컬럼을 NOT NULL DEFAULT 0으로 정규화 필요
--
-- 2. COALESCE 대신 OR 조건 사용 (현재 인덱스 활용도 개선)
--    → WHERE sigungu = '강남구' AND (is_deleted = 0 OR is_deleted IS NULL)
--    → MySQL이 ref_or_null 타입으로 인덱스 활용 가능
--
-- 3. 현재 상태로도 충분히 효율적
--    → rows=392로 적은 편
--    → filesort 비용도 낮음
--    → 하지만 대용량 데이터 증가 시 인덱스 정렬 활용 권장
--
-- 📈 성능 평가:
--    ✅ 현재: 매우 양호 (rows=392, type=ref)
--    ✅ 인덱스 활용: sigungu 조건 완벽 활용
--    ⚠️ 개선 여지: ORDER BY rating DESC 인덱스 정렬 활용
-- ============================================

-- ============================================
-- 15. findByEupmyeondong (개선됨: COALESCE 사용)
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE eupmyeondong = '역삼동' 
  AND (COALESCE(is_deleted, 0) = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: idx_locationservice_eupmyeondong (eupmyeondong, rating DESC)
# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref', 'idx_locationservice_sigungu', 'idx_locationservice_sigungu', '203', 'const', '392', '100.00', 'Using where'
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- | id | select_type  | table       | type   | possible_keys                          | key           | key_len | ref    | rows   | filtered | Extra                |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- |  1 | SIMPLE       | locationservice | ref    | idx_locationservice_sigungu | idx_locationservice_sigungu |       203 | const  | 392   |   100.00 | Using where                 |
-- +----+--------------+-------------+--------+------------+--------------------------------+---------+--------+-------+----------+----------------------+
-- ============================================

-- ============================================
-- 인덱스 사용 확인 방법
-- ============================================
-- EXPLAIN 결과에서 확인할 항목:
-- 1. type: ref, range, index 등이면 인덱스 사용 중
--    - ref: 인덱스로 특정 값 조회 (가장 효율적)
--    - range: 인덱스 범위 스캔 (BETWEEN, <, > 등)
--    - index: 인덱스 전체 스캔 (전체 스캔보다는 나음)
--    - ALL: 전체 테이블 스캔 (인덱스 미사용, 최악)
-- 2. key: 사용된 인덱스 이름 확인
--    - NULL이면 인덱스 미사용
--    - 인덱스명이 나오면 해당 인덱스 사용 중
-- 3. rows: 스캔한 행 수 (작을수록 좋음, 예상값)
-- 4. Extra: 
--    - Using index: 커버링 인덱스 (테이블 접근 없이 인덱스만 사용)
--    - Using where: WHERE 조건 필터링
--    - Using filesort: 정렬을 위한 임시 파일 사용 (성능 저하)
--    - Using index condition: 인덱스 조건 푸시다운 (MySQL 5.6+)

-- ============================================
-- 인덱스가 사용되지 않는 경우 확인
-- ============================================
-- type이 ALL이면 전체 테이블 스캔 (인덱스 미사용)
-- key가 NULL이면 인덱스 미사용
-- rows가 전체 행 수와 같으면 인덱스 미사용 가능성
-- Extra에 "Using filesort"가 나오면 ORDER BY에서 인덱스 미사용

-- ============================================
-- 성능 비교 (인덱스 적용 전/후)
-- ============================================
-- 인덱스 적용 전: type=ALL, rows=전체행수, key=NULL, Extra=Using filesort
-- 인덱스 적용 후: type=ref/range, rows=적용행수, key=인덱스명, Extra=Using index (가능시)

-- ============================================
-- ⚠️ 주의사항: COALESCE 함수 사용 시
-- ============================================
-- COALESCE(is_deleted, 0) = 0 조건은 함수를 사용하므로 인덱스를 직접 활용하지 못할 수 있음
-- 더 나은 방법: is_deleted 컬럼을 NOT NULL DEFAULT 0으로 변경하고 NULL을 허용하지 않기
-- 또는: (is_deleted = 0 OR is_deleted IS NULL) 조건 사용 (MySQL이 인덱스를 선택할 수 있음)
-- 
-- 하지만 현재는 COALESCE를 사용하도록 최적화했으므로, EXPLAIN으로 실제 동작 확인 필요

-- ============================================
-- 최적화 방안 (추가 고려사항)
-- ============================================
-- 1. is_deleted 컬럼 정규화: NULL → 0 (false)로 통일
--    ALTER TABLE locationservice MODIFY COLUMN is_deleted BOOLEAN NOT NULL DEFAULT 0;
--    이렇게 하면 (is_deleted = 0) 조건만으로 충분하고 인덱스 활용도 높아짐
--
-- 2. 복합 인덱스 순서: (지역필드, is_deleted, rating DESC)
--    예: CREATE INDEX idx_sido_deleted_rating ON locationservice(sido, is_deleted, rating DESC);
--    이렇게 하면 더 효율적인 인덱스 활용 가능
--
-- 3. 페이징 필수: ORDER BY rating DESC 시 LIMIT 사용하여 filesort 비용 절감

-- ============================================
-- 16. findTop10ByCategoryOrderByRatingDesc
-- 카테고리별 상위 10개 평점순 서비스 조회
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE ('병원' IS NULL OR category3 = '병원' OR category2 = '병원' OR category1 = '병원')
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC
LIMIT 10;

-- ✅ 예상 인덱스: idx_category3_deleted_rating, idx_locationservice_deleted_rating
-- ⚠️ LIMIT 10으로 filesort 비용 감소
-- ============================================

-- ============================================
-- 17. existsByNameAndAddress
-- 이름과 주소로 존재 여부 확인 (COUNT > 0)
-- ============================================
EXPLAIN 
SELECT COUNT(*) > 0 FROM locationservice 
WHERE name = '펫병원' 
  AND address = '서울시 강남구 테헤란로 123' 
  AND (is_deleted IS NULL OR is_deleted = 0);

-- ✅ 예상 인덱스: idx_name_address (name, address)
-- ✅ COUNT(*) > 0은 첫 번째 매칭 행만 찾으면 되므로 효율적
-- ============================================

-- ============================================
-- 18. findByRoadName
-- 도로명으로 서비스 조회
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE road_name = '테헤란로' 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ⚠️ 예상: road_name 인덱스가 없으면 전체 스캔 가능
-- 🔧 개선: CREATE INDEX idx_road_name ON locationservice(road_name, is_deleted, rating DESC);
-- ============================================

-- ============================================
-- 19. findByUserLocation
-- 사용자 위치 기반 검색 (시군구/읍면동)
-- ============================================
-- 시군구만 제공된 경우
EXPLAIN 
SELECT * FROM locationservice 
WHERE ('강남구' IS NULL OR sigungu = '강남구') 
  AND (NULL IS NULL OR eupmyeondong = NULL) 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- 시군구 + 읍면동 모두 제공된 경우
EXPLAIN 
SELECT * FROM locationservice 
WHERE ('강남구' IS NULL OR sigungu = '강남구') 
  AND ('역삼동' IS NULL OR eupmyeondong = '역삼동') 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: 
--    - sigungu만: idx_locationservice_sigungu (sigungu, rating DESC)
--    - sigungu + eupmyeondong: idx_locationservice_eupmyeondong (eupmyeondong, rating DESC)
-- ============================================

-- ============================================
-- 20. findByRadiusOrderByDistance
-- 거리 순 정렬 반경 검색 (길찾기용)
-- ============================================
-- 서울시청 기준 3km 이내 (위도: 37.5665, 경도: 126.9780)
-- 반경: 3000m
EXPLAIN 
SELECT * FROM locationservice 
WHERE latitude IS NOT NULL 
  AND longitude IS NOT NULL 
  AND ST_Distance_Sphere(POINT(longitude, latitude), POINT(126.9780, 37.5665)) <= 3000 
  AND (is_deleted IS NULL OR is_deleted = 0) 
ORDER BY ST_Distance_Sphere(POINT(longitude, latitude), POINT(126.9780, 37.5665)) ASC;

-- ⚠️ 성능 문제: ST_Distance_Sphere 함수 2회 호출 + filesort
-- ⚠️ 인덱스: ST_Distance_Sphere는 함수이므로 인덱스 불가
-- 🔧 개선 방안:
--    1. 좌표 범위로 1차 필터링 후 거리 계산
--       WHERE latitude BETWEEN :minLat AND :maxLat 
--         AND longitude BETWEEN :minLng AND :maxLng
--       → idx_lat_lng 인덱스 활용 가능
--    2. 공간 인덱스(Spatial Index) 사용 검토
--    3. 애플리케이션에서 거리 계산 및 정렬
-- ============================================

-- ============================================
-- 추가: findByLocationRange (is_deleted 조건 포함)
-- 위도/경도 범위 검색 + is_deleted 필터링
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE latitude BETWEEN 37.49 AND 37.52 
  AND longitude BETWEEN 126.98 AND 127.01 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: idx_lat_lng (latitude, longitude)
-- ⚠️ is_deleted 조건은 인덱스에 포함되지 않아 추가 필터링 필요
-- ============================================

-- ============================================
-- 추가: findByNameContaining (is_deleted 조건 포함)
-- 이름/설명으로 서비스 검색 + is_deleted 필터링
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE (name LIKE CONCAT('%', '반려동물', '%') 
    OR description LIKE CONCAT('%', '반려동물', '%') 
    OR category1 LIKE CONCAT('%', '반려동물', '%') 
    OR category2 LIKE CONCAT('%', '반려동물', '%') 
    OR category3 LIKE CONCAT('%', '반려동물', '%')) 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ⚠️ 성능 문제: LIKE '%keyword%' + OR 조건 5개 + filesort → 인덱스 불가
-- 🔧 개선: FULLTEXT INDEX 활용 (MATCH ... AGAINST)
EXPLAIN 
SELECT * FROM locationservice 
WHERE (MATCH(name, description) AGAINST('반려동물' IN BOOLEAN MODE) 
    OR category1 = '반려동물' 
    OR category2 = '반려동물' 
    OR category3 = '반려동물') 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: ft_name_desc (FULLTEXT 인덱스)
-- ============================================

-- ============================================
-- 추가: findByAddressContaining (is_deleted 조건 포함)
-- 주소로 서비스 검색 + is_deleted 필터링
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE (address LIKE CONCAT('%', '서울', '%') 
    OR sido LIKE CONCAT('%', '서울', '%') 
    OR sigungu LIKE CONCAT('%', '서울', '%') 
    OR eupmyeondong LIKE CONCAT('%', '서울', '%')) 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ⚠️ 성능 문제: LIKE '%keyword%' + OR 조건 4개 + filesort → 인덱스 불가
-- 🔧 개선: 정확한 매칭 필드(sido, sigungu, eupmyeondong)로 분리하여 애플리케이션에서 병합
-- ============================================

-- ============================================
-- 추가: findByRatingGreaterThanEqualOrderByRatingDesc (is_deleted 조건 포함)
-- 특정 평점 이상의 서비스 조회 + is_deleted 필터링
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE rating >= 4.0 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ✅ 예상 인덱스: idx_locationservice_deleted_rating (is_deleted, rating DESC)
-- ⚠️ rating >= 4.0 조건은 범위 검색이므로 인덱스 활용 가능
-- ============================================

-- ============================================
-- 추가: findBySeoulGuAndDong (is_deleted 조건 포함)
-- 서울 구/동 검색 + is_deleted 필터링
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE address LIKE CONCAT('%서울%', '강남구', '%') 
  AND ('역삼동' IS NULL OR address LIKE CONCAT('%', '역삼동', '%')) 
  AND (is_deleted IS NULL OR is_deleted = 0)
ORDER BY rating DESC;

-- ⚠️ 성능 문제: LIKE '%...%'는 인덱스 효율 낮음
-- 🔧 개선: sigungu, eupmyeondong 필드로 정확한 매칭 사용
-- ============================================

-- ============================================
-- 메서드별 인덱스 사용 요약
-- ============================================
-- ✅ 인덱스 잘 활용하는 메서드:
--    - findByOrderByRatingDesc: idx_locationservice_deleted_rating
--    - findBySido: idx_locationservice_sido
--    - findBySigungu: idx_locationservice_sigungu
--    - findByEupmyeondong: idx_locationservice_eupmyeondong
--    - findByRegion: idx_locationservice_eupmyeondong (가장 구체적인 조건 우선)
--    - findByLocationRange: idx_lat_lng
--    - findByNameAndAddress: idx_name_address
--    - findByAddress: idx_address_detail
--    - findByCategoryOrderByRatingDesc (category3만): idx_category3_deleted_rating
--
-- ⚠️ 인덱스 활용 어려운 메서드:
--    - findByNameContaining: LIKE '%...%' + OR 조건 → FULLTEXT INDEX 권장
--    - findByAddressContaining: LIKE '%...%' + OR 조건 → 정확한 매칭 필드 사용 권장
--    - findBySeoulGuAndDong: LIKE '%...%' → sigungu, eupmyeondong 필드 사용 권장
--    - findByRadius: ST_Distance_Sphere 함수 → 좌표 범위 필터링 권장
--    - findByRadiusOrderByDistance: ST_Distance_Sphere 함수 2회 → 좌표 범위 필터링 권장
--    - findByRoadName: road_name 인덱스 없음 → 인덱스 추가 권장
--
-- 🔧 추가 인덱스 권장사항:
--    1. CREATE INDEX idx_road_name ON locationservice(road_name, is_deleted, rating DESC);
--    2. CREATE INDEX idx_category2_deleted_rating ON locationservice(category2, is_deleted, rating DESC);
--    3. CREATE INDEX idx_category1_deleted_rating ON locationservice(category1, is_deleted, rating DESC);
-- ============================================

