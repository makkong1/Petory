-- LocationService 테이블 마이그레이션 SQL
-- 현재 엔티티 구조에 맞춘 수정 버전

-- ============================================
-- 삭제할 컬럼 (주의: description, rating, pet_friendly는 유지해야 함!)
-- MySQL 8.0.23 미만 버전에서는 IF EXISTS를 지원하지 않음
-- 컬럼이 존재하는지 먼저 확인 후 삭제하거나, 에러가 나도 무시하고 진행
-- ============================================

-- 방법 1: 컬럼 존재 여부 확인 후 삭제 (권장)
-- 아래 쿼리로 컬럼 존재 여부 확인:
-- SELECT COLUMN_NAME 
-- FROM INFORMATION_SCHEMA.COLUMNS 
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'locationservice' 
--   AND COLUMN_NAME IN ('opening_time', 'closing_time', 'pet_policy', 'image_url');

-- 방법 2: 직접 삭제 (컬럼이 없으면 에러 발생, 무시하고 다음 실행)
-- opening_time, closing_time 제거 (operating_hours로 통합)
ALTER TABLE locationservice DROP COLUMN opening_time;
ALTER TABLE locationservice DROP COLUMN closing_time;

-- pet_policy 제거 (pet_restrictions로 통합)
ALTER TABLE locationservice DROP COLUMN pet_policy;

-- image_url 제거 (사용하지 않음)
ALTER TABLE locationservice DROP COLUMN image_url;

-- 방법 3: MySQL 8.0.23+ 버전을 사용하는 경우 (IF EXISTS 지원)
-- ALTER TABLE locationservice DROP COLUMN IF EXISTS opening_time;
-- ALTER TABLE locationservice DROP COLUMN IF EXISTS closing_time;
-- ALTER TABLE locationservice DROP COLUMN IF EXISTS pet_policy;
-- ALTER TABLE locationservice DROP COLUMN IF EXISTS image_url;

-- 주의: 다음 컬럼들은 삭제하면 안됨!
-- description (place_description이 아니라 description으로 유지)
-- rating (평균 평점, 유지 필요)
-- pet_friendly (반려동물 동반 가능, 유지 필요)

-- ============================================
-- 추가할 컬럼 (엔티티 구조에 맞춤)
-- MySQL 5.7.4 미만 버전에서는 IF NOT EXISTS를 지원하지 않음
-- 컬럼이 이미 존재하면 에러 발생 (무시하고 다음 실행)
-- ============================================

-- 먼저 컬럼 존재 여부 확인 (선택사항):
-- SELECT COLUMN_NAME 
-- FROM INFORMATION_SCHEMA.COLUMNS 
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'locationservice' 
--   AND COLUMN_NAME IN ('category1', 'category2', 'category3', ...);

-- 카테고리 계층 구조
ALTER TABLE locationservice ADD COLUMN category1 VARCHAR(100);
ALTER TABLE locationservice ADD COLUMN category2 VARCHAR(100);
ALTER TABLE locationservice ADD COLUMN category3 VARCHAR(100);

-- 주소 구성 요소 (엔티티 필드명에 맞춤)
ALTER TABLE locationservice ADD COLUMN sido VARCHAR(50);              -- province가 아니라 sido
ALTER TABLE locationservice ADD COLUMN sigungu VARCHAR(50);           -- city가 아니라 sigungu
ALTER TABLE locationservice ADD COLUMN eupmyeondong VARCHAR(50);       -- town이 아니라 eupmyeondong
ALTER TABLE locationservice ADD COLUMN ri VARCHAR(50);                 -- village가 아니라 ri
ALTER TABLE locationservice ADD COLUMN bunji VARCHAR(100);             -- lot_number가 아니라 bunji
ALTER TABLE locationservice ADD COLUMN road_name VARCHAR(100);
ALTER TABLE locationservice ADD COLUMN building_number VARCHAR(50);

-- 주소 전체 (엔티티는 address, detail_address로 통합, road_address/lot_address는 추가 안함)
ALTER TABLE locationservice ADD COLUMN zip_code VARCHAR(10);          -- postal_code가 아니라 zip_code

-- 운영 정보
ALTER TABLE locationservice ADD COLUMN closed_day VARCHAR(255);        -- holiday가 아니라 closed_day
ALTER TABLE locationservice ADD COLUMN operating_hours VARCHAR(255);
ALTER TABLE locationservice ADD COLUMN parking_available BOOLEAN DEFAULT FALSE;

-- 가격 정보
ALTER TABLE locationservice ADD COLUMN price_info VARCHAR(255);       -- entrance_fee가 아니라 price_info

-- 반려동물 정책
-- pet_friendly는 이미 존재 (삭제하면 안됨!)
ALTER TABLE locationservice ADD COLUMN is_pet_only BOOLEAN;           -- pet_special_info가 아니라 is_pet_only (Boolean)
ALTER TABLE locationservice ADD COLUMN pet_size VARCHAR(100);         -- pet_size_allowed가 아니라 pet_size
ALTER TABLE locationservice ADD COLUMN pet_restrictions VARCHAR(255);
ALTER TABLE locationservice ADD COLUMN pet_extra_fee VARCHAR(255);    -- additional_pet_fee가 아니라 pet_extra_fee

-- 장소 정보
ALTER TABLE locationservice ADD COLUMN indoor BOOLEAN;                -- indoor_available이 아니라 indoor
ALTER TABLE locationservice ADD COLUMN outdoor BOOLEAN;               -- outdoor_available이 아니라 outdoor

-- 기타
-- description은 이미 존재 (place_description이 아니라 description으로 유지)
-- rating은 이미 존재 (유지 필요)
ALTER TABLE locationservice ADD COLUMN last_updated DATE;
ALTER TABLE locationservice ADD COLUMN data_source VARCHAR(50) DEFAULT 'PUBLIC';

-- ============================================
-- 기존 컬럼명 변경 (필요한 경우)
-- ============================================
-- 만약 기존에 다른 이름으로 존재한다면:
-- ALTER TABLE locationservice CHANGE COLUMN old_name new_name VARCHAR(255);

-- ============================================
-- 인덱스 추가 (성능 최적화)
-- MySQL 5.7.4 미만 버전에서는 IF NOT EXISTS를 지원하지 않음
-- 인덱스가 이미 존재하면 에러 발생 (무시하고 다음 실행)
-- ============================================
CREATE INDEX idx_category ON locationservice(category1, category2, category3);
CREATE INDEX idx_lat_lng ON locationservice(latitude, longitude);
CREATE INDEX idx_pet_friendly ON locationservice(pet_friendly);
CREATE INDEX idx_data_source ON locationservice(data_source);

-- MySQL 5.7.4+ 버전을 사용하는 경우 (IF NOT EXISTS 지원):
-- CREATE INDEX IF NOT EXISTS idx_category ON locationservice(category1, category2, category3);
-- CREATE INDEX IF NOT EXISTS idx_lat_lng ON locationservice(latitude, longitude);
-- CREATE INDEX IF NOT EXISTS idx_pet_friendly ON locationservice(pet_friendly);
-- CREATE INDEX IF NOT EXISTS idx_data_source ON locationservice(data_source);

-- ============================================
-- coordinates 컬럼 NULL 허용 (필수)
-- ============================================
-- coordinates가 NOT NULL로 되어 있으면 NULL 허용으로 변경
ALTER TABLE locationservice MODIFY COLUMN coordinates POINT NULL;

-- ============================================
-- 확인 쿼리
-- ============================================
-- DESCRIBE locationservice;
-- SHOW COLUMNS FROM locationservice;

