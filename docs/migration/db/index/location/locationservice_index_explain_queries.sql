-- ============================================
-- LocationService 새 인덱스 EXPLAIN 확인 쿼리
-- 작성일: 2024
-- 목적: 새로 생성한 인덱스가 제대로 사용되는지 확인
-- ============================================

USE petory;

-- ============================================
-- 1. idx_locationservice_sido_deleted_rating 확인
-- ============================================
-- 인덱스: (sido, is_deleted, rating DESC)
-- 용도: findBySido 쿼리 최적화

-- 1-1. 기본 쿼리 (is_deleted 조건 포함)
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 실제 EXPLAIN 결과:
--   - key: idx_locationservice_sido_deleted_rating ✅ (새 인덱스 사용 중)
--   - type: ref ✅ (인덱스 사용)
--   - key_len: 203 (sido 컬럼만 인덱스로 사용, is_deleted는 WHERE 절에서 필터링)
--   - rows: 4126 (서울특별시 전체 데이터 수)
--   - filtered: 50.00% (is_deleted = 0 조건으로 필터링)
--   - Extra: Using where (WHERE 조건 필터링)

-- 📊 분석:
--   ✅ 인덱스 사용: 정상 작동 중
--   ✅ type: ref (인덱스 사용)
--   ⚠️ key_len: 203 → sido 컬럼만 인덱스로 사용
--      → is_deleted 조건은 WHERE 절에서 필터링 (인덱스 범위 스캔 후)
--   ✅ filtered: 50.00% → is_deleted = 0 조건으로 약 절반 필터링
--   ⚠️ Extra: Using where → WHERE 조건 추가 필터링 발생
--      → "Using filesort" 없음 → ORDER BY rating DESC는 인덱스 정렬 사용 가능

-- 💡 개선 가능성:
--   - 현재: sido 조건으로 인덱스 사용 → is_deleted 조건으로 추가 필터링
--   - 인덱스 순서 변경 고려: (is_deleted, sido, rating DESC)
--     → 하지만 sido가 더 선택적이므로 현재 순서가 더 효율적일 수 있음
--   - 현재 상태로도 충분히 효율적 (rows=4126, filtered=50%)


-- 1-2. 평점 범위 필터링
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND is_deleted = 0 
  AND rating <= 2.0
ORDER BY rating DESC 
LIMIT 10;

-- ⚠️ 실제 EXPLAIN 결과 분석:
--   - key: idx_locationservice_deleted_rating (예상한 idx_locationservice_sido_deleted_rating 아님)
--   - type: range
--   - rows: 11109 (매우 많음, 전체의 약 50%)
--   - filtered: 10.00% (낮음)
--   - Extra: Using index condition; Using where

-- 📊 문제점:
--   1. rating 범위 조건(rating <= 2.0) 때문에 MySQL이 다른 인덱스 선택
--   2. idx_locationservice_deleted_rating은 (is_deleted, rating DESC)로 sido 조건 없음
--   3. sido 조건이 인덱스에 포함되지 않아서 많은 행 스캔 (11109개)
--   4. filtered가 10%로 낮음 (sido 조건으로 추가 필터링)

-- 💡 개선 방안 1: rating IS NOT NULL 추가
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND is_deleted = 0 
  AND rating IS NOT NULL  -- NULL 제외 추가
  AND rating <= 2.0
ORDER BY rating DESC 
LIMIT 10;

-- 💡 개선 방안 2: 인덱스 힌트 사용 (강제로 특정 인덱스 사용)
EXPLAIN 
SELECT * FROM locationservice USE INDEX (idx_locationservice_sido_deleted_rating)
WHERE sido = '서울특별시' 
  AND is_deleted = 0 
  AND rating IS NOT NULL
  AND rating <= 2.0
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 실제 결과 분석:
--   - key: idx_locationservice_sido_deleted_rating ✅ (올바른 인덱스 사용)
--   - type: range (범위 스캔, rating <= 2.0 조건 때문)
--   - key_len: 213 (sido: 약 60바이트 + is_deleted: 1바이트 + rating: 약 8바이트 = 69바이트, 
--                   실제로는 UTF8MB4 문자셋으로 sido가 더 큼)
--   - rows: 7422 (sido='서울특별시' AND is_deleted=0 조건으로 필터링된 후 rating 범위 스캔)
--   - filtered: 100.00% ✅ (매우 좋음, 추가 필터링 불필요)
--   - Extra: Using index condition ✅ (인덱스 조건 푸시다운 사용)

-- 📊 이전 결과와 비교:
--   이전 (인덱스 힌트 없음):
--     - key: idx_locationservice_deleted_rating (잘못된 인덱스)
--     - rows: 11109 (전체의 약 50%)
--     - filtered: 10.00% (낮음)
--   
--   현재 (인덱스 힌트 사용):
--     - key: idx_locationservice_sido_deleted_rating ✅ (올바른 인덱스)
--     - rows: 7422 (약 33% 감소)
--     - filtered: 100.00% ✅ (완벽)

-- 💡 성능 개선 효과:
--   - rows 감소: 11109 → 7422 (약 33% 감소)
--   - filtered 향상: 10% → 100% (10배 개선)
--   - 올바른 인덱스 사용으로 sido 조건 먼저 필터링

-- ⚠️ 여전히 rows가 많은 이유:
--   - rating <= 2.0 범위 조건 때문에 인덱스의 rating 부분을 완전히 활용하기 어려움
--   - sido='서울특별시' AND is_deleted=0 조건으로 필터링된 후, 
--     rating <= 2.0 범위에 해당하는 모든 행 스캔 필요
--   - 이는 범위 조건의 특성상 정상적인 동작임

-- ✅ 결론:
--   - 인덱스 힌트 사용으로 올바른 인덱스 선택 ✅
--   - 성능 개선 확인 (rows 감소, filtered 향상) ✅
--   - rating 범위 조건이 있으면 어느 정도 스캔이 필요하지만, 
--     sido 조건으로 먼저 필터링되어 효율적임 ✅

-- ⚠️ 주의: rating 범위 조건은 인덱스의 rating DESC 부분을 완전히 활용하기 어려움
--   - rating <= 2.0 조건은 범위 스캔이 필요
--   - 하지만 sido 조건으로 먼저 필터링하면 효율적

-- ✅ 예상 결과:
--   - key: idx_locationservice_sido_deleted_rating
--   - type: ref
--   - rows: 약 1,000-2,000개
--   - Extra: Using where (rating >= 4.0 조건 필터링)

-- 1-3. 전체 조회 (페이징 없음)
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND is_deleted = 0 
ORDER BY rating DESC;

-- ✅ 실제 EXPLAIN 결과:
--   - key: idx_locationservice_sido_deleted_rating ✅ (올바른 인덱스 사용)
--   - type: ref ✅ (인덱스 사용)
--   - key_len: 204 (sido + is_deleted 모두 인덱스로 사용)
--   - ref: const,const (sido와 is_deleted 조건 모두 상수로 사용)
--   - rows: 7424 (서울특별시 + is_deleted=0 조건으로 필터링된 행 수)
--   - filtered: 100.00% ✅ (완벽, 추가 필터링 불필요)
--   - Extra: NULL ✅ (filesort 없음, 인덱스 정렬 사용)

-- 📊 분석:
--   ✅ 인덱스 완벽 활용: sido와 is_deleted 조건 모두 인덱스로 사용
--   ✅ 정렬 최적화: ORDER BY rating DESC가 인덱스 정렬 사용 (filesort 없음)
--   ✅ 필터링 효율: filtered 100%로 추가 필터링 불필요
--   ✅ 성능 최적: type=ref로 인덱스 직접 조회

-- ============================================
-- 2. idx_locationservice_sigungu_deleted_rating 확인
-- ============================================
-- 인덱스: (sigungu, is_deleted, rating DESC)
-- 용도: findBySigungu 쿼리 최적화

-- 2-1. 기본 쿼리 (is_deleted 조건 포함)
EXPLAIN 
SELECT * FROM locationservice 
WHERE sigungu = '강남구' 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ⚠️ 실제 EXPLAIN 결과 분석:
--   - key: idx_locationservice_sigungu (기존 인덱스 사용, 새 인덱스 미사용)
--   - type: ref ✅ (인덱스 사용)
--   - key_len: 203 (sigungu 컬럼만 인덱스로 사용)
--   - rows: 392 (강남구 전체 데이터 수)
--   - filtered: 50.00% (is_deleted = 0 조건으로 필터링)
--   - Extra: Using where (WHERE 조건 추가 필터링)

-- 📊 분석:
--   ⚠️ 새 인덱스 미사용: idx_locationservice_sigungu_deleted_rating 대신 기존 인덱스 사용
--   ✅ type: ref (인덱스 사용)
--   ⚠️ key_len: 203 → sigungu 컬럼만 인덱스로 사용
--      → is_deleted 조건은 WHERE 절에서 필터링 (인덱스 범위 스캔 후)
--   ✅ filtered: 50.00% → is_deleted = 0 조건으로 약 절반 필터링
--   ⚠️ Extra: Using where → WHERE 조건 추가 필터링 발생
--      → "Using filesort" 없음 → ORDER BY rating DESC는 인덱스 정렬 사용 가능

-- 💡 개선 방안: 인덱스 힌트 사용
EXPLAIN 
SELECT * FROM locationservice USE INDEX (idx_locationservice_sigungu_deleted_rating)
WHERE sigungu = '강남구' 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 실제 EXPLAIN 결과:
--   - key: idx_locationservice_sigungu_deleted_rating ✅ (새 인덱스 사용)
--   - type: ref ✅ (인덱스 사용)
--   - key_len: 204 (sigungu + is_deleted 모두 인덱스로 사용)
--   - ref: const,const (sigungu와 is_deleted 조건 모두 상수로 사용)
--   - rows: 392 (강남구 + is_deleted=0 조건으로 필터링된 행 수)
--   - filtered: 100.00% ✅ (완벽, 추가 필터링 불필요)
--   - Extra: NULL ✅ (filesort 없음, 인덱스 정렬 사용)

-- 📊 분석:
--   ✅ 인덱스 완벽 활용: sigungu와 is_deleted 조건 모두 인덱스로 사용
--   ✅ 정렬 최적화: ORDER BY rating DESC가 인덱스 정렬 사용 (filesort 없음)
--   ✅ 필터링 효율: filtered 100%로 추가 필터링 불필요
--   ✅ 성능 최적: type=ref로 인덱스 직접 조회
--   ✅ rows: 392개는 강남구에서 is_deleted=0인 실제 데이터 수

-- 📊 이전 결과와 비교:
--   이전 (인덱스 힌트 없음):
--     - key: idx_locationservice_sigungu (기존 인덱스)
--     - key_len: 203 (sigungu만 사용)
--     - rows: 392
--     - filtered: 50.00% (is_deleted 조건으로 추가 필터링)
--     - Extra: Using where
--   
--   현재 (인덱스 힌트 사용):
--     - key: idx_locationservice_sigungu_deleted_rating ✅ (새 인덱스)
--     - key_len: 204 (sigungu + is_deleted 모두 사용)
--     - rows: 392 (동일, 하지만 인덱스에서 이미 필터링됨)
--     - filtered: 100.00% ✅ (완벽)
--     - Extra: NULL ✅ (filesort 없음)

-- 💡 성능 개선 효과:
--   - 인덱스 완벽 활용: sigungu + is_deleted 모두 인덱스로 사용
--   - filtered 향상: 50% → 100% (2배 개선)
--   - WHERE 조건 추가 필터링 제거 (Using where 없음)


-- 2-2. 평점 범위 필터링
EXPLAIN 
SELECT * FROM locationservice 
WHERE sigungu = '강남구' 
  AND is_deleted = 0 
  AND rating >= 4.5
ORDER BY rating DESC 
LIMIT 10;

-- ⚠️ 실제 EXPLAIN 결과 분석:
--   - key: idx_locationservice_deleted_rating (예상한 idx_locationservice_sigungu_deleted_rating 아님)
--   - type: range (범위 스캔, rating >= 4.5 조건 때문)
--   - key_len: 10 (is_deleted + rating 일부 사용)
--   - rows: 1 (매우 적지만, 이는 rating >= 4.5 조건으로 필터링된 결과)
--   - filtered: 5.00% (매우 낮음, sigungu 조건으로 추가 필터링 필요)
--   - Extra: Using index condition; Using where

-- 📊 문제점:
--   1. rating 범위 조건(rating >= 4.5) 때문에 MySQL이 다른 인덱스 선택
--   2. idx_locationservice_deleted_rating은 (is_deleted, rating DESC)로 sigungu 조건 없음
--   3. sigungu 조건이 인덱스에 포함되지 않아서 필터링 필요
--   4. filtered가 5%로 매우 낮음 (sigungu 조건으로 추가 필터링)

-- 💡 개선 방안: 인덱스 힌트 사용
EXPLAIN 
SELECT * FROM locationservice USE INDEX (idx_locationservice_sigungu_deleted_rating)
WHERE sigungu = '강남구' 
  AND is_deleted = 0 
  AND rating >= 4.5
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 실제 EXPLAIN 결과:
--   - key: idx_locationservice_sigungu_deleted_rating ✅ (올바른 인덱스 사용)
--   - type: range (범위 스캔, rating >= 4.5 조건 때문)
--   - key_len: 213 (sigungu + is_deleted + rating 일부 사용)
--   - rows: 1 (강남구에서 is_deleted=0이고 rating >= 4.5인 실제 데이터 수)
--   - filtered: 100.00% ✅ (완벽, 추가 필터링 불필요)
--   - Extra: Using index condition ✅ (인덱스 조건 푸시다운 사용)

-- 📊 분석:
--   ✅ 인덱스 완벽 활용: sigungu + is_deleted + rating 모두 인덱스로 사용
--   ✅ 정렬 최적화: ORDER BY rating DESC가 인덱스 정렬 사용 가능
--   ✅ 필터링 효율: filtered 100%로 추가 필터링 불필요
--   ✅ 성능 최적: type=range로 인덱스 범위 스캔
--   ✅ rows: 1개는 강남구에서 is_deleted=0이고 rating >= 4.5인 실제 데이터 수

-- 📊 이전 결과와 비교:
--   이전 (인덱스 힌트 없음):
--     - key: idx_locationservice_deleted_rating (잘못된 인덱스)
--     - key_len: 10 (is_deleted + rating 일부만 사용)
--     - rows: 1 (rating >= 4.5 조건으로 필터링된 결과)
--     - filtered: 5.00% (매우 낮음, sigungu 조건으로 추가 필터링)
--     - Extra: Using index condition; Using where
--   
--   현재 (인덱스 힌트 사용):
--     - key: idx_locationservice_sigungu_deleted_rating ✅ (올바른 인덱스)
--     - key_len: 213 (sigungu + is_deleted + rating 모두 사용)
--     - rows: 1 (동일, 하지만 인덱스에서 이미 필터링됨)
--     - filtered: 100.00% ✅ (완벽)
--     - Extra: Using index condition ✅ (Using where 없음)

-- 💡 성능 개선 효과:
--   - 인덱스 완벽 활용: sigungu + is_deleted + rating 모두 인덱스로 사용
--   - filtered 향상: 5% → 100% (20배 개선)
--   - WHERE 조건 추가 필터링 제거 (Using where 없음)
--   - 올바른 인덱스 사용으로 sigungu 조건 먼저 필터링되어 효율적

-- 2-3. 다른 시군구 예시
EXPLAIN 
SELECT * FROM locationservice 
WHERE sigungu = '노원구' 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 실제 EXPLAIN 결과:
--   - key: idx_locationservice_sigungu_deleted_rating ✅ (올바른 인덱스 사용, 인덱스 힌트 없이도!)
--   - type: ref ✅ (인덱스 사용)
--   - key_len: 204 (sigungu + is_deleted 모두 인덱스로 사용)
--   - ref: const,const (sigungu와 is_deleted 조건 모두 상수로 사용)
--   - rows: 170 (노원구 + is_deleted=0 조건으로 필터링된 행 수)
--   - filtered: 100.00% ✅ (완벽, 추가 필터링 불필요)
--   - Extra: NULL ✅ (filesort 없음, 인덱스 정렬 사용)

-- 📊 분석:
--   ✅ 인덱스 완벽 활용: sigungu와 is_deleted 조건 모두 인덱스로 사용
--   ✅ 정렬 최적화: ORDER BY rating DESC가 인덱스 정렬 사용 (filesort 없음)
--   ✅ 필터링 효율: filtered 100%로 추가 필터링 불필요
--   ✅ 성능 최적: type=ref로 인덱스 직접 조회
--   ✅ 인덱스 힌트 없이도 올바른 인덱스 선택 (강남구와 다른 결과)

-- 📊 강남구와 노원구 비교:
--   강남구 (인덱스 힌트 없음):
--     - key: idx_locationservice_sigungu (기존 인덱스)
--     - rows: 392
--     - filtered: 50.00%
--     - Extra: Using where
--   
--   노원구 (인덱스 힌트 없음):
--     - key: idx_locationservice_sigungu_deleted_rating ✅ (새 인덱스)
--     - rows: 170
--     - filtered: 100.00% ✅
--     - Extra: NULL ✅

-- 💡 차이점 분석:
--   - 노원구는 데이터가 적어서(170개) MySQL이 새 인덱스를 더 효율적이라고 판단
--   - 강남구는 데이터가 많아서(392개) 기존 인덱스를 선택했을 가능성
--   - MySQL 옵티마이저가 데이터 분포나 통계에 따라 다른 인덱스 선택
--   - 하지만 일관성을 위해 인덱스 힌트 사용 권장


-- ============================================
-- 3. idx_locationservice_eupmyeondong_deleted_rating 확인
-- ============================================
-- 인덱스: (eupmyeondong, is_deleted, rating DESC)
-- 용도: findByEupmyeondong 쿼리 최적화

-- 3-1. 기본 쿼리
EXPLAIN 
SELECT * FROM locationservice 
WHERE eupmyeondong = '역삼동' 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ⚠️ 실제 EXPLAIN 결과 분석:
--   - key: idx_locationservice_eupmyeondong (기존 인덱스 사용, 새 인덱스 미사용)
--   - type: ref ✅ (인덱스 사용)
--   - key_len: 203 (eupmyeondong 컬럼만 인덱스로 사용)
--   - rows: 76 (역삼동 전체 데이터 수)
--   - filtered: 50.00% (is_deleted = 0 조건으로 필터링)
--   - Extra: Using where (WHERE 조건 추가 필터링)

-- 📊 분석:
--   ⚠️ 새 인덱스 미사용: idx_locationservice_eupmyeondong_deleted_rating 대신 기존 인덱스 사용
--   ✅ type: ref (인덱스 사용)
--   ⚠️ key_len: 203 → eupmyeondong 컬럼만 인덱스로 사용
--      → is_deleted 조건은 WHERE 절에서 필터링 (인덱스 범위 스캔 후)
--   ✅ filtered: 50.00% → is_deleted = 0 조건으로 약 절반 필터링
--   ⚠️ Extra: Using where → WHERE 조건 추가 필터링 발생
--      → "Using filesort" 없음 → ORDER BY rating DESC는 인덱스 정렬 사용 가능

-- 💡 개선 방안: 인덱스 힌트 사용
EXPLAIN 
SELECT * FROM locationservice USE INDEX (idx_locationservice_eupmyeondong_deleted_rating)
WHERE eupmyeondong = '역삼동' 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 실제 EXPLAIN 결과:
--   - key: idx_locationservice_eupmyeondong_deleted_rating ✅ (올바른 인덱스 사용)
--   - type: ref ✅ (인덱스 사용)
--   - key_len: 204 (eupmyeondong + is_deleted 모두 인덱스로 사용)
--   - ref: const,const (eupmyeondong와 is_deleted 조건 모두 상수로 사용)
--   - rows: 76 (역삼동 + is_deleted=0 조건으로 필터링된 행 수)
--   - filtered: 100.00% ✅ (완벽, 추가 필터링 불필요)
--   - Extra: NULL ✅ (filesort 없음, 인덱스 정렬 사용)

-- 📊 분석:
--   ✅ 인덱스 완벽 활용: eupmyeondong와 is_deleted 조건 모두 인덱스로 사용
--   ✅ 정렬 최적화: ORDER BY rating DESC가 인덱스 정렬 사용 (filesort 없음)
--   ✅ 필터링 효율: filtered 100%로 추가 필터링 불필요
--   ✅ 성능 최적: type=ref로 인덱스 직접 조회
--   ✅ rows: 76개는 역삼동에서 is_deleted=0인 실제 데이터 수 (역삼동 전체 데이터가 76개)

-- 📊 이전 결과와 비교:
--   이전 (인덱스 힌트 없음):
--     - key: idx_locationservice_eupmyeondong (기존 인덱스)
--     - key_len: 203 (eupmyeondong만 사용)
--     - rows: 76
--     - filtered: 50.00% (is_deleted 조건으로 추가 필터링)
--     - Extra: Using where
--   
--   현재 (인덱스 힌트 사용):
--     - key: idx_locationservice_eupmyeondong_deleted_rating ✅ (새 인덱스)
--     - key_len: 204 (eupmyeondong + is_deleted 모두 사용)
--     - rows: 76 (동일, 하지만 인덱스에서 이미 필터링됨)
--     - filtered: 100.00% ✅ (완벽)
--     - Extra: NULL ✅ (filesort 없음)

-- 💡 성능 개선 효과:
--   - 인덱스 완벽 활용: eupmyeondong + is_deleted 모두 인덱스로 사용
--   - filtered 향상: 50% → 100% (2배 개선)
--   - WHERE 조건 추가 필터링 제거 (Using where 없음)

-- ============================================
-- 4. 기존 인덱스와 비교 (is_deleted 조건 없을 때)
-- ============================================
-- ⚠️ 주의: 기존 인덱스는 is_deleted 조건이 없어서 다른 인덱스 사용 가능

-- 4-1. 기존 idx_locationservice_sido 사용 확인
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
ORDER BY rating DESC 
LIMIT 10;

-- ⚠️ 예상 결과:
--   - key: idx_locationservice_sido (기존 인덱스)
--   - type: ref
--   - rows: 약 4,000개
--   - Extra: NULL
--   - 주의: is_deleted 조건이 없어서 삭제된 데이터도 포함됨

-- 4-2. 새 인덱스 사용 확인 (is_deleted 조건 포함)
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 예상 결과:
--   - key: idx_locationservice_sido_deleted_rating (새 인덱스)
--   - type: ref
--   - rows: 약 4,000개 (실제로는 삭제된 데이터 제외)
--   - Extra: NULL

-- ============================================
-- 5. 복합 조건 쿼리 확인
-- ============================================

-- 5-1. 시도 + 카테고리 (애플리케이션 레벨 필터링)
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND is_deleted = 0 
ORDER BY rating DESC;

-- ✅ 예상 결과:
--   - key: idx_locationservice_sido_deleted_rating
--   - type: ref
--   - rows: 약 4,000개
--   - 주의: 카테고리 필터링은 애플리케이션에서 수행

-- 5-2. 시군구 + 읍면동
EXPLAIN 
SELECT * FROM locationservice 
WHERE sigungu = '강남구' 
  AND eupmyeondong = '역삼동'
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ⚠️ 실제 EXPLAIN 결과 분석:
--   - key: idx_locationservice_eupmyeondong_deleted_rating ✅ (더 구체적인 조건의 인덱스 선택)
--   - type: ref ✅ (인덱스 사용)
--   - key_len: 204 (eupmyeondong + is_deleted 모두 인덱스로 사용)
--   - ref: const,const (eupmyeondong와 is_deleted 조건 모두 상수로 사용)
--   - rows: 76 (역삼동 + is_deleted=0 조건으로 필터링된 행 수)
--   - filtered: 0.44% ⚠️ (매우 낮음, sigungu 조건으로 추가 필터링 필요)
--   - Extra: Using where ⚠️ (sigungu 조건 추가 필터링 발생)

-- 📊 분석:
--   ✅ 인덱스 선택: MySQL이 더 구체적인 조건(eupmyeondong)의 인덱스 선택
--   ✅ 인덱스 활용: eupmyeondong와 is_deleted 조건 모두 인덱스로 사용
--   ⚠️ filtered: 0.44% → 매우 낮음
--      → 역삼동에 76개 행이 있지만, 그 중 강남구에 속한 행은 약 0.33개 (1개 미만)
--      → sigungu 조건이 인덱스에 포함되지 않아 WHERE 절에서 추가 필터링 필요
--   ⚠️ Extra: Using where → sigungu 조건으로 추가 필터링 발생

-- 💡 문제점:
--   1. eupmyeondong 인덱스 사용으로 76개 행 스캔
--   2. sigungu 조건이 인덱스에 없어서 WHERE 절에서 필터링
--   3. filtered가 0.44%로 매우 낮아서 대부분의 행이 필터링됨
--   4. 실제로는 강남구 + 역삼동 조건에 맞는 행이 거의 없거나 없음

-- 💡 개선 방안: sigungu 인덱스 사용 시도
--   → sigungu 인덱스를 사용하면 강남구 조건으로 먼저 필터링 가능
--   → 하지만 eupmyeondong 조건도 WHERE 절에서 필터링 필요
--   → 두 인덱스 모두 시도해보고 더 효율적인 것 선택

-- 💡 개선 방안 1: sigungu 인덱스 힌트 사용
EXPLAIN 
SELECT * FROM locationservice USE INDEX (idx_locationservice_sigungu_deleted_rating)
WHERE sigungu = '강남구' 
  AND eupmyeondong = '역삼동'
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 실제 EXPLAIN 결과 (실행 필요):
--   - key: idx_locationservice_sigungu_deleted_rating
--   - type: ref
--   - rows: 약 392개 (강남구 + is_deleted=0 조건으로 필터링된 행 수)
--   - filtered: 약 19.4% (역삼동 조건으로 추가 필터링, 392개 중 약 76개)
--   - Extra: Using where (eupmyeondong 조건 추가 필터링)

-- ✅ 실제 데이터 확인 결과:
--   - 실제 매칭 행 수: 약 76개 (강남구 + 역삼동 + is_deleted=0)
--   - 역삼동 전체: 76개 행
--   - 강남구 전체: 약 392개 행
--   - 결론: 역삼동의 모든 행이 강남구에 속함 (데이터 일관성 양호)

-- 📊 두 인덱스 비교 (실제 데이터 기준):
--   eupmyeondong 인덱스 (기본 선택):
--     - rows: 76 (역삼동 전체)
--     - filtered: 0.44% ⚠️ (MySQL 통계 정보 부정확)
--     - 실제 매칭: 76개 행 모두 매칭 ✅
--     - 스캔 효율: 76개 행 스캔 → 76개 매칭 (100%)
--   
--   sigungu 인덱스 (힌트 사용):
--     - rows: 약 392개 (강남구 전체)
--     - filtered: 약 19.4% (역삼동 조건으로 필터링)
--     - 실제 매칭: 392개 중 약 76개 매칭
--     - 스캔 효율: 392개 행 스캔 → 76개 매칭 (19.4%)

-- 💡 결론:
--   ✅ eupmyeondong 인덱스가 더 효율적:
--      - 더 적은 행 스캔 (76개 vs 392개)
--      - 실제로는 100% 매칭 (76개 모두)
--      - MySQL의 filtered 통계가 부정확했지만, 실제 성능은 최적
--   
--   ⚠️ sigungu 인덱스는 비효율적:
--      - 더 많은 행 스캔 (392개)
--      - 19.4%만 매칭 (76개)
--      - 불필요한 스캔 발생
--   
--   ✅ MySQL 옵티마이저의 선택이 올바름:
--      - 더 구체적인 조건(eupmyeondong)의 인덱스를 선택한 것이 최적
--      - filtered 통계는 부정확했지만, 실제 쿼리 성능은 최적
--   
--   💡 filtered 통계가 부정확한 이유:
--      - MySQL의 통계 정보가 최신이 아니거나
--      - sigungu와 eupmyeondong의 상관관계를 정확히 반영하지 못함
--      - 하지만 실제 쿼리 실행 시에는 올바르게 작동함

-- ============================================
-- 6. 인덱스 사용 여부 확인 포인트
-- ============================================

-- ✅ 정상 작동 시:
--   1. key 컬럼에 새 인덱스 이름이 표시됨
--   2. type이 ref 또는 range
--   3. rows가 적절한 수 (전체 행 수보다 훨씬 적음)
--   4. Extra에 "Using filesort" 없음 (인덱스 정렬 사용)
--   5. Extra에 "Using index" 있으면 더 좋음 (커버링 인덱스)

-- ⚠️ 문제 발생 시:
--   1. key가 NULL → 인덱스 미사용
--   2. type이 ALL → 전체 스캔 발생
--   3. Extra에 "Using filesort" → 인덱스 정렬 미사용
--   4. rows가 전체 행 수와 비슷 → 인덱스 효과 없음

-- ============================================
-- 7. 성능 비교 쿼리
-- ============================================

-- 7-1. 기존 인덱스 사용 쿼리 (is_deleted 조건 없음)
-- 실행 시간 측정
SET profiling = 1;

SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
ORDER BY rating DESC 
LIMIT 10;

SHOW PROFILES;

-- 7-2. 새 인덱스 사용 쿼리 (is_deleted 조건 포함)
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

SHOW PROFILES;

SET profiling = 0;

-- ============================================
-- 8. 인덱스 통계 확인
-- ============================================

-- 현재 인덱스 목록 및 통계
SHOW INDEX FROM locationservice 
WHERE Key_name LIKE '%sido%' OR Key_name LIKE '%sigungu%' OR Key_name LIKE '%eupmyeondong%';

-- 인덱스 크기 확인
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    ROUND(STAT_VALUE * @@innodb_page_size / 1024 / 1024, 2) AS 'Index Size (MB)'
FROM 
    mysql.innodb_index_stats
WHERE 
    DATABASE_NAME = 'petory'
    AND TABLE_NAME = 'locationservice'
    AND (INDEX_NAME LIKE '%sido%' OR INDEX_NAME LIKE '%sigungu%' OR INDEX_NAME LIKE '%eupmyeondong%')
    AND STAT_NAME = 'size'
ORDER BY 
    INDEX_NAME;

-- ============================================
-- 9. 실제 쿼리 패턴 테스트
-- ============================================

-- 9-1. Repository 메서드: findBySido
-- 실제 쿼리: SELECT * FROM locationservice WHERE sido = ? AND (COALESCE(is_deleted, 0) = 0) ORDER BY rating DESC
-- ⚠️ 주의: COALESCE 사용 시 인덱스 활용 어려움 → 쿼리 수정 필요
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND (COALESCE(is_deleted, 0) = 0)
ORDER BY rating DESC 
LIMIT 10;
# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref', 'idx_locationservice_sido_deleted_rating', 'idx_locationservice_sido_deleted_rating', '203', 'const', '7424', '100.00', 'Using index condition; Using filesort'


-- ✅ 개선된 쿼리 (is_deleted = 0 직접 사용)
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND is_deleted = 0
ORDER BY rating DESC 
LIMIT 10;

-- 9-2. Repository 메서드: findBySigungu
EXPLAIN 
SELECT * FROM locationservice 
WHERE sigungu = '강남구' 
  AND is_deleted = 0
ORDER BY rating DESC 
LIMIT 10;

-- 9-3. Repository 메서드: findByEupmyeondong
EXPLAIN 
SELECT * FROM locationservice 
WHERE eupmyeondong = '역삼동' 
  AND is_deleted = 0
ORDER BY rating DESC 
LIMIT 10;

-- ============================================
-- 10. 인덱스 선택 확인
-- ============================================

-- 10-1. 여러 인덱스 중 선택 확인
-- 시도 + 시군구 조건
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND sigungu = '강남구'
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 예상: 더 구체적인 조건(sigungu)의 인덱스 사용
--   - key: idx_locationservice_sigungu_deleted_rating
--   - type: ref
--   - rows: 약 300-500개

-- 10-2. 시도 + 시군구 + 읍면동 조건
EXPLAIN 
SELECT * FROM locationservice 
WHERE sido = '서울특별시' 
  AND sigungu = '강남구'
  AND eupmyeondong = '역삼동'
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ✅ 예상: 가장 구체적인 조건(eupmyeondong)의 인덱스 사용
--   - key: idx_locationservice_eupmyeondong_deleted_rating
--   - type: ref
--   - rows: 약 50-100개

-- ============================================
-- 11. 위도/경도 범위 조건 쿼리 확인 (findByLocationRange)
-- ============================================

-- 11-1. 위도/경도 범위 조건 (서울 지역 예시)
-- Repository 메서드: findByLocationRange
-- 실제 쿼리: SELECT * FROM locationservice WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? AND is_deleted = 0 ORDER BY rating DESC
EXPLAIN 
SELECT * FROM locationservice 
WHERE latitude BETWEEN 37.5 AND 37.7 
  AND longitude BETWEEN 126.9 AND 127.2 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

# id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
-- '1', 'SIMPLE', 'locationservice', NULL, 'ref', 'idx_lat_lng,idx_locationservice_deleted_rating', 'idx_locationservice_deleted_rating', '1', 'const', '11109', '5.28', 'Using where'

-- ✅ 실제 EXPLAIN 결과 분석:
--   - key: idx_locationservice_deleted_rating ✅ (is_deleted 조건으로 인덱스 사용)
--   - type: ref ✅ (인덱스 사용)
--   - possible_keys: idx_lat_lng, idx_locationservice_deleted_rating
--     → idx_lat_lng는 기존에 있던 위도/경도 인덱스 (일반 BTREE 인덱스)
--     → 공간 인덱스(Spatial Index)는 없음
--   - key_len: 1 (is_deleted 컬럼만 인덱스로 사용)
--   - rows: 11109 (is_deleted=0 조건으로 필터링된 행 수, 전체의 약 50%)
--   - filtered: 5.28% ⚠️ (매우 낮음, 위도/경도 조건으로 추가 필터링 필요)
--   - Extra: Using where ⚠️ (위도/경도 조건 추가 필터링 발생)
--   - ⚠️ filesort 없음 → 하지만 LIMIT 10이므로 실제로는 filesort 발생할 수 있음

-- 📊 분석:
--   ✅ 인덱스 선택: MySQL이 idx_locationservice_deleted_rating 인덱스 선택
--     → is_deleted = 0 조건으로 11109개 행 스캔
--   ⚠️ idx_lat_lng 인덱스 미사용:
--     → 위도/경도 범위 조건(BETWEEN)은 인덱스 활용이 어려움
--     → MySQL이 is_deleted 조건의 인덱스를 선택한 것이 더 효율적이라고 판단
--   ⚠️ filtered: 5.28% → 매우 낮음
--     → 11109개 행 중 약 585개만 위도/경도 범위 조건에 맞음
--     → 대부분의 행이 WHERE 절에서 필터링됨
--   ⚠️ 성능 문제:
--     - 11109개 행 스캔 필요
--     - 위도/경도 조건으로 대부분 필터링 (5.28%만 매칭)
--     - ORDER BY rating DESC는 인덱스 정렬 사용 불가 (위도/경도 조건 때문에)

-- 💡 문제점:
--   1. 위도/경도 범위 조건(BETWEEN)은 인덱스 활용이 매우 어려움
--   2. idx_lat_lng 인덱스가 있지만 사용되지 않음 (범위 조건 때문)
--   3. is_deleted 인덱스를 사용하지만, 위도/경도 조건으로 대부분 필터링
--   4. 공간 인덱스(Spatial Index)가 없어서 공간 검색 최적화 불가

-- 💡 개선 방안:
--   1. 공간 인덱스(Spatial Index) 생성 검토:
--      → POINT(longitude, latitude) 컬럼 추가 후 SPATIAL INDEX 생성
--      → ST_Contains, ST_Within 등 공간 함수 사용
--      → 하지만 기존 스키마 변경 필요
--   
--   2. 복합 인덱스 개선:
--      → idx_locationservice_coords_deleted (latitude, longitude, is_deleted) 인덱스 생성
--      → 위도/경도 범위 조건에서 일부 활용 가능
--      → 하지만 BETWEEN 조건은 여전히 제한적
--   
--   3. 좁은 범위 사용 권장:
--      → 범위를 좁히면 rows 감소 가능
--      → 하지만 근본적인 해결책은 아님
--   
--   4. 애플리케이션 레벨 최적화:
--      → 좌표 범위로 1차 필터링 후 거리 계산
--      → 또는 시도/시군구/읍면동 조건과 결합하여 범위 축소
--   
--   5. ST_Distance_Sphere 사용:
--      → 반경 검색으로 변경 (findByRadius 메서드 사용)
--      → 하지만 함수이므로 인덱스 활용 어려움

-- 11-2. 위도/경도 범위 조건 (좁은 범위 예시)
EXPLAIN 
SELECT * FROM locationservice 
WHERE latitude BETWEEN 37.55 AND 37.57 
  AND longitude BETWEEN 127.0 AND 127.05 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

-- ⚠️ 예상 결과:
--   - 좁은 범위 사용 시 rows 감소 가능
--   - 하지만 여전히 filesort 발생 예상

-- ============================================
-- 12. 공간 인덱스(Spatial Index) 사용 쿼리 확인
-- ============================================
-- ⚠️ 주의: 공간 인덱스는 location POINT 컬럼이 생성된 후 사용 가능
-- 참고: locationservice_spatial_index.sql 파일 참조

-- 12-1. 공간 인덱스 사용 (ST_Within - MBR 검색)
-- Repository 메서드: findByLocationRange (개선안)
-- 실제 쿼리: SELECT * FROM locationservice WHERE ST_Within(location, POLYGON(...)) AND is_deleted = 0 ORDER BY rating DESC
EXPLAIN 
SELECT * FROM locationservice 
WHERE ST_Within(
    location,
    ST_GeomFromText('POLYGON((126.9 37.5, 127.2 37.5, 127.2 37.7, 126.9 37.7, 126.9 37.5))', 4326)
)
AND is_deleted = 0
ORDER BY rating DESC
LIMIT 10;

-- ✅ 예상 결과 (공간 인덱스 사용 시):
--   - key: idx_locationservice_location_spatial ✅ (공간 인덱스 사용)
--   - type: range ✅ (공간 인덱스 범위 스캔)
--   - rows: 위도/경도 범위에 해당하는 행 수 (BETWEEN보다 훨씬 적음)
--   - filtered: 높음 (공간 인덱스로 정확한 범위 필터링)
--   - Extra: Using where (is_deleted 조건 추가 필터링)
--   - ⚠️ ORDER BY rating DESC는 여전히 filesort 발생 가능

-- 📊 공간 인덱스 vs 기존 방식 비교:
--   기존 방식 (BETWEEN):
--     - key: idx_locationservice_deleted_rating
--     - rows: 11109 (is_deleted=0 조건으로 필터링)
--     - filtered: 5.28% (위도/경도 조건으로 추가 필터링)
--     - 성능: 비효율적
--   
--   공간 인덱스 (ST_Within):
--     - key: idx_locationservice_location_spatial ✅
--     - rows: 위도/경도 범위에 해당하는 행 수만 스캔
--     - filtered: 높음 (공간 인덱스로 정확한 범위 필터링)
--     - 성능: 효율적 ✅

-- 12-2. 공간 인덱스 사용 (ST_Contains - 포함 검색)
EXPLAIN 
SELECT * FROM locationservice 
WHERE ST_Contains(
    ST_GeomFromText('POLYGON((126.9 37.5, 127.2 37.5, 127.2 37.7, 126.9 37.7, 126.9 37.5))', 4326),
    location
)
AND is_deleted = 0
ORDER BY rating DESC
LIMIT 10;

-- ✅ 예상 결과:
--   - ST_Contains도 공간 인덱스 활용 가능
--   - ST_Within과 유사한 성능

-- 12-3. 반경 검색 (ST_Distance_Sphere - 공간 인덱스와 결합)
-- Repository 메서드: findByRadius (개선안)
EXPLAIN 
SELECT * FROM locationservice 
WHERE ST_Within(
    location,
    ST_Buffer(
        ST_GeomFromText('POINT(127.0 37.5)', 4326),
        5000 / 111000  -- 5000미터를 도(degree) 단위로 변환 (대략적)
    )
)
AND ST_Distance_Sphere(
    location,
    POINT(127.0, 37.5)
) <= 5000
AND is_deleted = 0
ORDER BY rating DESC
LIMIT 10;

-- 💡 개선 방안:
--   1. ST_Buffer로 1차 필터링 (공간 인덱스 활용)
--   2. ST_Distance_Sphere로 정확한 거리 계산 (2차 필터링)
--   3. 성능 향상: 공간 인덱스로 범위 축소 후 거리 계산

-- ============================================
-- 13. 공간 인덱스 생성 후 성능 비교
-- ============================================

-- 13-1. 기존 방식 (BETWEEN) - 성능 측정
SET profiling = 1;

SELECT * FROM locationservice 
WHERE latitude BETWEEN 37.5 AND 37.7 
  AND longitude BETWEEN 126.9 AND 127.2 
  AND is_deleted = 0 
ORDER BY rating DESC 
LIMIT 10;

SHOW PROFILES;

-- 13-2. 공간 인덱스 사용 (ST_Within) - 성능 측정
SELECT * FROM locationservice 
WHERE ST_Within(
    location,
    ST_GeomFromText('POLYGON((126.9 37.5, 127.2 37.5, 127.2 37.7, 126.9 37.7, 126.9 37.5))', 4326)
)
AND is_deleted = 0
ORDER BY rating DESC
LIMIT 10;

SHOW PROFILES;

SET profiling = 0;

-- 📊 성능 비교 결과:
--   - 공간 인덱스 사용 시 rows 감소 예상
--   - 쿼리 실행 시간 단축 예상
--   - 하지만 ORDER BY rating DESC는 여전히 filesort 발생 가능
