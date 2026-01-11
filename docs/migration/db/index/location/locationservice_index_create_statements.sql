-- ============================================
-- LocationService 인덱스 생성 SQL (MySQL 버전별 호환)
-- 작성일: 2024
-- ============================================

-- ============================================
-- MySQL 버전별 구문 차이
-- ============================================
-- MySQL 5.6 이하: CREATE INDEX에서 ALGORITHM/LOCK 옵션 미지원
-- MySQL 5.7+: CREATE INDEX에서 ALGORITHM/LOCK 옵션 지원
-- 
-- 권장: ALTER TABLE ... ADD INDEX 형식 사용 (모든 버전 호환)

-- ============================================
-- 방법 1: CREATE INDEX (간단한 방법)
-- ============================================
-- MySQL 5.7+에서 ALGORITHM/LOCK 옵션 사용하려면 ALTER TABLE 사용
-- 기본 CREATE INDEX는 ALGORITHM=INPLACE, LOCK=SHARED로 실행됨

-- 최우선 인덱스 생성 (⭐⭐⭐⭐⭐)
CREATE INDEX idx_locationservice_sido_deleted_rating 
ON locationservice(sido, is_deleted, rating DESC);

CREATE INDEX idx_locationservice_sigungu_deleted_rating 
ON locationservice(sigungu, is_deleted, rating DESC);

CREATE INDEX idx_locationservice_eupmyeondong_deleted_rating 
ON locationservice(eupmyeondong, is_deleted, rating DESC);

-- 높은 우선순위 인덱스 생성 (⭐⭐⭐⭐)
CREATE INDEX idx_locationservice_road_name_deleted_rating 
ON locationservice(road_name, is_deleted, rating DESC);

CREATE INDEX idx_locationservice_category2_deleted_rating 
ON locationservice(category2, is_deleted, rating DESC);

CREATE INDEX idx_locationservice_name_address_deleted 
ON locationservice(name, address, is_deleted);

CREATE INDEX idx_locationservice_address_deleted 
ON locationservice(address, is_deleted);

-- 중간 우선순위 인덱스 생성 (⭐⭐⭐)
CREATE INDEX idx_locationservice_category1_deleted_rating 
ON locationservice(category1, is_deleted, rating DESC);

CREATE INDEX idx_locationservice_coords_deleted 
ON locationservice(latitude, longitude, is_deleted);

-- ============================================
-- 방법 2: ALTER TABLE (ALGORITHM/LOCK 옵션 사용)
-- ============================================
-- MySQL 5.6+ 모든 버전에서 지원
-- LOCK=NONE 옵션으로 쓰기 락 없이 인덱스 생성 가능

-- 최우선 인덱스 생성 (⭐⭐⭐⭐⭐)
ALTER TABLE locationservice 
ADD INDEX idx_locationservice_sido_deleted_rating (sido, is_deleted, rating DESC)
ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE locationservice 
ADD INDEX idx_locationservice_sigungu_deleted_rating (sigungu, is_deleted, rating DESC)
ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE locationservice 
ADD INDEX idx_locationservice_eupmyeondong_deleted_rating (eupmyeondong, is_deleted, rating DESC)
ALGORITHM=INPLACE, LOCK=NONE;

-- 높은 우선순위 인덱스 생성 (⭐⭐⭐⭐)
ALTER TABLE locationservice 
ADD INDEX idx_locationservice_road_name_deleted_rating (road_name, is_deleted, rating DESC)
ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE locationservice 
ADD INDEX idx_locationservice_category2_deleted_rating (category2, is_deleted, rating DESC)
ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE locationservice 
ADD INDEX idx_locationservice_name_address_deleted (name, address, is_deleted)
ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE locationservice 
ADD INDEX idx_locationservice_address_deleted (address, is_deleted)
ALGORITHM=INPLACE, LOCK=NONE;

-- 중간 우선순위 인덱스 생성 (⭐⭐⭐)
ALTER TABLE locationservice 
ADD INDEX idx_locationservice_category1_deleted_rating (category1, is_deleted, rating DESC)
ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE locationservice 
ADD INDEX idx_locationservice_coords_deleted (latitude, longitude, is_deleted)
ALGORITHM=INPLACE, LOCK=NONE;

-- ============================================
-- 참고사항
-- ============================================
-- 1. ALGORITHM=INPLACE: 테이블 복사 없이 인덱스 생성 (빠름)
-- 2. LOCK=NONE: 쓰기 락 없이 인덱스 생성 (다운타임 없음)
-- 3. LOCK=SHARED: 읽기만 가능, 쓰기 불가 (기본값)
-- 4. LOCK=EXCLUSIVE: 읽기/쓰기 모두 불가 (최대 락)
--
-- 권장: 운영 환경에서는 ALTER TABLE ... ALGORITHM=INPLACE, LOCK=NONE 사용
