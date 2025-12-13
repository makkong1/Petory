-- ============================================
-- Users 테이블에 nickname 컬럼 추가 DDL
-- ============================================

-- 1. Users 테이블에 nickname 컬럼 추가
ALTER TABLE users
ADD COLUMN nickname VARCHAR(50) NULL COMMENT '닉네임 (소셜 로그인 사용자 필수 설정)',
ADD UNIQUE KEY uk_users_nickname (nickname);

-- 2. 기존 username을 nickname으로 마이그레이션 (선택사항)
-- UPDATE users SET nickname = username WHERE nickname IS NULL;

-- 3. 인덱스는 UNIQUE KEY로 자동 생성됨

-- ============================================
-- 설명:
-- ============================================
-- nickname: 
--   - 소셜 로그인 사용자가 설정하는 닉네임
--   - NULL 허용 (처음 로그인 시 설정 전까지는 NULL)
--   - UNIQUE 제약조건 (중복 불가)
--   - 최대 50자
--
-- 기존 username과의 관계:
--   - username: 시스템 내부 사용 (로그인 ID, 자동 생성)
--   - nickname: 사용자에게 표시되는 이름 (사용자 설정)
--
-- 소셜 로그인 플로우:
--   1. 소셜 로그인 성공 시 nickname이 NULL이면 설정 필요
--   2. 닉네임 설정 API 호출하여 설정
--   3. 이후 닉네임으로 사용자 식별 및 표시

