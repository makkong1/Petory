-- LocationService 테이블에서 불필요한 컬럼 삭제
-- 실행 전 백업 필수!

-- 1. 사용 안 되는 컬럼 삭제 (즉시 삭제 가능)
ALTER TABLE locationservice DROP COLUMN IF EXISTS ri;
ALTER TABLE locationservice DROP COLUMN IF EXISTS bunji;
ALTER TABLE locationservice DROP COLUMN IF EXISTS building_number;

-- 2. 중복 컬럼 삭제 (Repository 수정 완료 후)
-- category 필드 삭제 (category3, category2, category1로 대체)
ALTER TABLE locationservice DROP COLUMN IF EXISTS category;

-- detailAddress 필드 삭제 (address로 통합)
ALTER TABLE locationservice DROP COLUMN IF EXISTS detail_address;

-- 확인 쿼리
-- SELECT COLUMN_NAME, DATA_TYPE 
-- FROM INFORMATION_SCHEMA.COLUMNS 
-- WHERE TABLE_NAME = 'locationservice' 
-- ORDER BY ORDINAL_POSITION;

