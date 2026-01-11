-- ============================================
-- LocationService 테이블 인덱스 권장사항
-- 작성일: 2024
-- 분석 기준: LocationServiceService.java, LocationService.java, SpringDataJpaLocationServiceRepository.java
-- ============================================
--
-- 실제 테이블 스키마 정보 (정규화 완료):
--   - is_deleted: TINYINT(1) NOT NULL, Default: 0, Key: MUL (인덱스 존재)
--   - deleted_at: datetime, NULL 허용
--
-- ✅ 정규화 완료: is_deleted 컬럼이 NOT NULL로 변경됨
--   - 이제 WHERE is_deleted = 0 조건만 사용하면 됨 (가장 효율적)
--   - type: ref (인덱스 완벽 활용)
--   - COALESCE 함수나 OR 조건 불필요
--
-- ============================================

-- ============================================
-- 1. 기본 인덱스 (Soft Delete + Rating 정렬)
-- ============================================
-- 용도: 평점순 전체 조회 (findByOrderByRatingDesc)
-- 쿼리 패턴: WHERE is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐⭐⭐ (최우선)
-- 
-- ✅ 정규화 완료: is_deleted가 NOT NULL이므로 WHERE is_deleted = 0 만 사용
--    → type: ref (인덱스 완벽 활용)
CREATE INDEX idx_locationservice_deleted_rating 
ON locationservice(is_deleted, rating DESC);

-- ============================================
-- 2. 지역별 검색 인덱스 (정확한 매칭)
-- ============================================

-- 2-1. 시도별 조회
-- 용도: findBySido
-- 쿼리 패턴: WHERE sido = ? AND is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐⭐⭐
CREATE INDEX idx_locationservice_sido_deleted_rating 
ON locationservice(sido, is_deleted, rating DESC);

-- 2-2. 시군구별 조회
-- 용도: findBySigungu
-- 쿼리 패턴: WHERE sigungu = ? AND is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐⭐⭐
CREATE INDEX idx_locationservice_sigungu_deleted_rating 
ON locationservice(sigungu, is_deleted, rating DESC);

-- 2-3. 읍면동별 조회
-- 용도: findByEupmyeondong
-- 쿼리 패턴: WHERE eupmyeondong = ? AND is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐⭐⭐
CREATE INDEX idx_locationservice_eupmyeondong_deleted_rating 
ON locationservice(eupmyeondong, is_deleted, rating DESC);

-- 2-4. 도로명별 조회
-- 용도: findByRoadName
-- 쿼리: WHERE road_name = ? AND is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐⭐
CREATE INDEX idx_locationservice_road_name_deleted_rating 
ON locationservice(road_name, is_deleted, rating DESC);

-- ============================================
-- 3. 카테고리별 검색 인덱스
-- ============================================

-- 3-1. 카테고리3 (소분류) - 가장 구체적
-- 용도: findByCategoryOrderByRatingDesc, findTop10ByCategoryOrderByRatingDesc
-- 쿼리: WHERE category3 = ? AND is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐⭐⭐
CREATE INDEX idx_locationservice_category3_deleted_rating 
ON locationservice(category3, is_deleted, rating DESC);

-- 3-2. 카테고리2 (중분류)
-- 용도: findByCategoryOrderByRatingDesc (category2 매칭)
-- 쿼리: WHERE category2 = ? AND is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐⭐
CREATE INDEX idx_locationservice_category2_deleted_rating 
ON locationservice(category2, is_deleted, rating DESC);

-- 3-3. 카테고리1 (대분류)
-- 용도: findByCategoryOrderByRatingDesc (category1 매칭)
-- 쿼리: WHERE category1 = ? AND is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐
CREATE INDEX idx_locationservice_category1_deleted_rating 
ON locationservice(category1, is_deleted, rating DESC);

-- ============================================
-- 4. 위치 기반 검색 인덱스
-- ============================================

-- 4-1. 위도/경도 범위 검색
-- 용도: findByLocationRange
-- 쿼리: WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? ORDER BY rating DESC
-- 우선순위: ⭐⭐⭐⭐
CREATE INDEX idx_locationservice_latitude_longitude 
ON locationservice(latitude, longitude);

-- 4-2. 반경 검색 최적화 (위도/경도 + is_deleted)
-- 용도: findByRadius (1차 필터링)
-- 쿼리: WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND ... AND is_deleted = 0
-- 우선순위: ⭐⭐⭐
-- 참고: ST_Distance_Sphere는 함수이므로 인덱스 불가, 좌표 범위로 1차 필터링 후 거리 계산 권장
CREATE INDEX idx_locationservice_coords_deleted 
ON locationservice(latitude, longitude, is_deleted);

-- ============================================
-- 5. 중복 체크 인덱스
-- ============================================

-- 5-1. 이름 + 주소 중복 체크
-- 용도: findByNameAndAddress, existsByNameAndAddress
-- 쿼리: WHERE name = ? AND address = ? AND (is_deleted IS NULL OR is_deleted = false)
-- 우선순위: ⭐⭐⭐⭐
CREATE INDEX idx_locationservice_name_address_deleted 
ON locationservice(name, address, is_deleted);

-- 5-2. 주소 중복 체크
-- 용도: findByAddress
-- 쿼리: WHERE address = ? AND is_deleted = 0
-- 우선순위: ⭐⭐⭐
CREATE INDEX idx_locationservice_address_deleted 
ON locationservice(address, is_deleted);

-- ============================================
-- 6. 텍스트 검색 인덱스 (FULLTEXT)
-- ============================================

-- 6-1. 이름 + 설명 FULLTEXT 검색
-- 용도: findByNameContaining (개선안: FULLTEXT 사용)
-- 쿼리: WHERE MATCH(name, description) AGAINST(? IN BOOLEAN MODE) AND is_deleted = 0
-- 우선순위: ⭐⭐⭐
-- 참고: LIKE '%keyword%'는 인덱스 효율이 낮으므로 FULLTEXT INDEX 권장
CREATE FULLTEXT INDEX idx_locationservice_name_description_ft 
ON locationservice(name, description) WITH PARSER ngram;

-- ============================================
-- 7. 복합 검색 인덱스 (선택적)
-- ============================================

-- 7-1. 지역 + 카테고리 복합 검색
-- 용도: 지역별 검색 후 카테고리 필터링 (애플리케이션 레벨 필터링 대신)
-- 우선순위: ⭐⭐ (애플리케이션에서 필터링하는 경우 불필요)
-- 참고: 현재 코드는 애플리케이션에서 카테고리 필터링하므로 선택적

-- 7-2. 평점 범위 검색
-- 용도: findByRatingGreaterThanEqualOrderByRatingDesc
-- 쿼리: WHERE rating >= ? AND is_deleted = 0 ORDER BY rating DESC
-- 우선순위: ⭐⭐ (idx_locationservice_deleted_rating으로 커버 가능)
-- 참고: idx_locationservice_deleted_rating 인덱스로 범위 검색 가능

-- ============================================
-- 인덱스 우선순위 요약
-- ============================================
-- ⭐⭐⭐⭐⭐ (최우선 - 즉시 생성 권장):
--   1. idx_locationservice_deleted_rating
--   2. idx_locationservice_sido_deleted_rating
--   3. idx_locationservice_sigungu_deleted_rating
--   4. idx_locationservice_eupmyeondong_deleted_rating
--   5. idx_locationservice_category3_deleted_rating
--
-- ⭐⭐⭐⭐ (높은 우선순위):
--   6. idx_locationservice_road_name_deleted_rating
--   7. idx_locationservice_category2_deleted_rating
--   8. idx_locationservice_latitude_longitude
--   9. idx_locationservice_name_address_deleted
--
-- ⭐⭐⭐ (중간 우선순위):
--   10. idx_locationservice_category1_deleted_rating
--   11. idx_locationservice_coords_deleted
--   12. idx_locationservice_address_deleted
--   13. idx_locationservice_name_description_ft

-- ============================================
-- 주의사항
-- ============================================
-- 1. is_deleted 컬럼 스키마 정보 (정규화 완료):
--    - 타입: TINYINT(1) NOT NULL
--    - NULL 허용: NO (정규화 완료)
--    - 기본값: 0
--    - 인덱스: MUL (이미 인덱스 존재)
--    
--    ✅ 정규화 완료로 인덱스 활용 최적화:
--    - 이제 WHERE is_deleted = 0 조건만 사용하면 됨 (가장 효율적)
--    - type: ref (인덱스 완벽 활용)
--    - COALESCE 함수나 OR 조건 불필요
--    - 쿼리 단순화 및 성능 향상
--
-- 2. MySQL 8.0.17+ Display Width 경고:
--    - TINYINT(1)의 display width는 deprecated 됨
--    - 향후 버전에서는 TINYINT만 사용 권장
--    - 현재는 기능상 문제 없음 (경고만 발생)
--    - 필요시: ALTER TABLE locationservice MODIFY COLUMN is_deleted TINYINT NOT NULL DEFAULT 0;
--
-- 3. 인덱스 생성 순서:
--    - 우선순위 높은 인덱스부터 순차 생성
--    - 대용량 테이블의 경우 인덱스 생성 시간 소요 (ONLINE DDL 사용 권장)
--    - 예: CREATE INDEX ... ALGORITHM=INPLACE, LOCK=NONE;
--
-- 4. 인덱스 모니터링:
--    - EXPLAIN으로 인덱스 사용 여부 확인
--    - type이 ref, ref_or_null, range 등이면 인덱스 활용 중
--    - type이 ALL이면 전체 스캔 (인덱스 미사용)
--    - 사용하지 않는 인덱스는 제거 고려 (저장 공간 절약)
--
-- 5. 쿼리 최적화:
--    - OR 조건 (category3 OR category2 OR category1)은 UNION으로 분리 고려
--    - ST_Distance_Sphere 함수는 좌표 범위로 1차 필터링 후 거리 계산 권장
--    - LIKE '%keyword%'는 FULLTEXT INDEX 사용 권장
--    - COALESCE 대신 (is_deleted IS NULL OR is_deleted = 0) 패턴 사용 권장

-- ============================================
-- 기존 인덱스와의 호환성
-- ============================================
-- 기존 indexes.sql에 정의된 인덱스:
--   - idx_locationservice_latitude_longitude (유지)
--   - idx_locationservice_rating_desc (idx_locationservice_deleted_rating으로 대체)
--   - idx_locationservice_category_rating (category3, category2, category1로 분리)
--   - idx_locationservice_name_address (idx_locationservice_name_address_deleted으로 확장)
--   - idx_locationservice_address (idx_locationservice_address_deleted으로 확장)
--
-- 기존 인덱스 제거 고려:
--   - idx_locationservice_rating_desc (idx_locationservice_deleted_rating으로 대체)
--   - idx_locationservice_category_rating (카테고리별로 분리된 인덱스로 대체)

-- ============================================
-- 인덱스 생성 스크립트 실행
-- ============================================
-- 1. 우선순위 높은 인덱스부터 생성
-- 2. 각 인덱스 생성 후 EXPLAIN으로 확인
-- 3. 성능 테스트 수행
-- 4. 필요시 인덱스 조정

-- ============================================
-- 쿼리 최적화 가이드 (is_deleted 정규화 완료)
-- ============================================
-- 
-- ✅ 정규화 완료: is_deleted 컬럼이 NOT NULL로 변경됨
--    - 이제 가장 효율적인 패턴 사용 가능
--
-- 🎯 최적의 패턴 (현재 사용 가능):
--    WHERE is_deleted = 0
--    → type: ref (인덱스 완벽 활용)
--    → 가장 단순하고 효율적
--
-- 📝 Repository 쿼리 개선 권장사항:
--    1. Native Query 개선:
--       기존: WHERE (COALESCE(is_deleted, 0) = 0)
--       개선: WHERE is_deleted = 0
--
--    2. JPQL 개선:
--       기존: WHERE (is_deleted IS NULL OR is_deleted = false)
--       개선: WHERE is_deleted = false (또는 is_deleted = 0)
--
--    3. 정규화 완료로 인한 이점:
--       - COALESCE 함수 제거 가능
--       - OR 조건 제거 가능
--       - 쿼리 단순화
--       - 인덱스 활용도 최대화 (type: ref)
--       - 성능 향상
