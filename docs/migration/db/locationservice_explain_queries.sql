-- ============================================
-- LocationService Repository 메서드 EXPLAIN 쿼리
-- 워크벤치에서 실행하여 인덱스 사용 여부 확인
-- ============================================

USE petory;

-- ============================================
-- 1. findByCategoryOrderByRatingDesc
-- 카테고리별 평점순 서비스 조회
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE category = '병원' 
ORDER BY rating DESC;

-- 예상 인덱스: idx_category_rating 사용
-- ============================================


-- ============================================
-- 2. findByOrderByRatingDesc
-- 평점순 전체 서비스 조회
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
ORDER BY rating DESC;

-- 예상 인덱스: idx_rating_desc 사용
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
-- ============================================


-- ============================================
-- 5. findByRegion
-- 전국 지역 검색 (시/도 > 시/군/구 > 동/면/리)
-- ============================================
-- 서울시 강남구 역삼동 예시
EXPLAIN 
SELECT * FROM locationservice 
WHERE ('서울특별시' IS NULL OR address LIKE '%서울특별시%') 
  AND ('강남구' IS NULL OR address LIKE '%강남구%') 
  AND ('역삼동' IS NULL OR address LIKE '%역삼동%') 
ORDER BY rating DESC;

-- 예상 인덱스: idx_address (prefix match), 하지만 LIKE '%...%'는 인덱스 효율 낮음
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

-- FULLTEXT 인덱스 사용 쿼리 (더 효율적)
EXPLAIN 
SELECT * FROM locationservice 
WHERE MATCH(name, description) AGAINST('반려동물' IN BOOLEAN MODE) 
ORDER BY rating DESC;

-- 예상 인덱스: ft_name_desc (FULLTEXT 인덱스)
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
-- ============================================


-- ============================================
-- 10. findByNameAndAddress
-- 이름과 주소로 중복 체크
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE name = '펫병원' AND address = '서울시 강남구 테헤란로 123';

-- 예상 인덱스: idx_name_address 사용
-- ============================================


-- ============================================
-- 11. findByAddress
-- 주소로 중복 체크
-- ============================================
EXPLAIN 
SELECT * FROM locationservice 
WHERE address = '서울시 강남구 테헤란로 123';

-- 예상 인덱스: idx_address 사용
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
-- 인덱스 사용 확인 방법
-- ============================================
-- EXPLAIN 결과에서 확인할 항목:
-- 1. type: ref, range, index 등이면 인덱스 사용 중
-- 2. key: 사용된 인덱스 이름 확인
-- 3. rows: 스캔한 행 수 (작을수록 좋음)
-- 4. Extra: Using index, Using where, Using filesort 등 확인

-- ============================================
-- 인덱스가 사용되지 않는 경우 확인
-- ============================================
-- type이 ALL이면 전체 테이블 스캔 (인덱스 미사용)
-- key가 NULL이면 인덱스 미사용
-- rows가 전체 행 수와 같으면 인덱스 미사용 가능성

-- ============================================
-- 성능 비교 (인덱스 적용 전/후)
-- ============================================
-- 인덱스 적용 전: type=ALL, rows=전체행수
-- 인덱스 적용 후: type=ref/range, rows=적용행수, key=인덱스명

