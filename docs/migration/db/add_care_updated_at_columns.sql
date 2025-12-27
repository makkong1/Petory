-- ============================================
-- BaseTimeEntity를 상속받는 테이블에 updated_at 컬럼 추가
-- ============================================
-- 
-- 문제: 여러 엔티티가 BaseTimeEntity를 상속받아 updated_at 컬럼이 필요하지만 
--       실제 DB 테이블에는 이 컬럼이 없어서 에러 발생
--
-- 해결: 관련 테이블에 updated_at 컬럼 추가
-- ============================================

-- carerequest 테이블에 updated_at 컬럼 추가
ALTER TABLE carerequest 
ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
AFTER created_at;

-- 기존 데이터의 updated_at을 created_at과 동일하게 설정
UPDATE carerequest 
SET updated_at = created_at 
WHERE updated_at IS NULL AND created_at IS NOT NULL;

-- 컬럼을 NOT NULL로 변경
ALTER TABLE carerequest 
MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- careapplication 테이블에 updated_at 컬럼 추가
ALTER TABLE careapplication 
ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
AFTER created_at;

-- 기존 데이터의 updated_at을 created_at과 동일하게 설정
UPDATE careapplication 
SET updated_at = created_at 
WHERE updated_at IS NULL AND created_at IS NOT NULL;

-- 컬럼을 NOT NULL로 변경
ALTER TABLE careapplication 
MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- carereview 테이블에 updated_at 컬럼 추가
ALTER TABLE carereview 
ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
AFTER created_at;

-- 기존 데이터의 updated_at을 created_at과 동일하게 설정
UPDATE carereview 
SET updated_at = created_at 
WHERE updated_at IS NULL AND created_at IS NOT NULL;

-- 컬럼을 NOT NULL로 변경
ALTER TABLE carereview 
MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- comment 테이블에 updated_at 컬럼 추가
ALTER TABLE comment 
ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
AFTER created_at;

-- 기존 데이터의 updated_at을 created_at과 동일하게 설정
UPDATE comment 
SET updated_at = created_at 
WHERE updated_at IS NULL AND created_at IS NOT NULL;

-- 컬럼을 NOT NULL로 변경
ALTER TABLE comment 
MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- pets 테이블에 updated_at 컬럼 추가
ALTER TABLE pets 
ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
AFTER created_at;

-- 기존 데이터의 updated_at을 created_at과 동일하게 설정
UPDATE pets 
SET updated_at = created_at 
WHERE updated_at IS NULL AND created_at IS NOT NULL;

-- 컬럼을 NOT NULL로 변경
ALTER TABLE pets 
MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- pet_vaccinations 테이블에 updated_at 컬럼 추가
ALTER TABLE pet_vaccinations 
ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
AFTER created_at;

-- 기존 데이터의 updated_at을 created_at과 동일하게 설정
UPDATE pet_vaccinations 
SET updated_at = created_at 
WHERE updated_at IS NULL AND created_at IS NOT NULL;

-- 컬럼을 NOT NULL로 변경
ALTER TABLE pet_vaccinations 
MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

