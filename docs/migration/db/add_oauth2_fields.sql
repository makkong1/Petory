-- ============================================
-- OAuth2 소셜 로그인 필드 추가 DDL
-- ============================================

-- 1. Users 테이블에 OAuth2 관련 필드 추가
ALTER TABLE users
ADD COLUMN profile_image VARCHAR(500) NULL COMMENT '프로필 이미지 URL (구글 picture, 네이버 profile_image)',
ADD COLUMN birth_date VARCHAR(20) NULL COMMENT '생년월일 (네이버: birthyear + birthday 조합, 형식: YYYY-MM-DD)',
ADD COLUMN gender VARCHAR(10) NULL COMMENT '성별 (네이버: M/F, 구글: 제공 안 함)',
ADD COLUMN email_verified BOOLEAN DEFAULT FALSE COMMENT '이메일 인증 여부 (구글: email_verified, 네이버: 기본 true)';

-- 2. SocialUser 테이블에 Provider별 상세 정보 필드 추가
ALTER TABLE socialuser
ADD COLUMN provider_data TEXT NULL COMMENT 'Provider별 원본 데이터 JSON (모든 OAuth2 응답 데이터 저장)',
ADD COLUMN provider_profile_image VARCHAR(500) NULL COMMENT 'Provider별 프로필 이미지 URL',
ADD COLUMN provider_name VARCHAR(100) NULL COMMENT 'Provider별 이름 (구글: given_name + family_name, 네이버: name)',
ADD COLUMN provider_phone VARCHAR(50) NULL COMMENT 'Provider별 전화번호 (네이버: mobile 또는 mobile_e164)',
ADD COLUMN provider_age_range VARCHAR(20) NULL COMMENT 'Provider별 나이대 (네이버: 20-29 형식)';

-- 3. 인덱스 추가 (선택사항 - 성능 최적화)
-- provider_data는 TEXT이므로 인덱스 불가, 다른 필드들은 필요시 추가
-- CREATE INDEX idx_socialuser_provider_providerid ON socialuser(provider, provider_id);
-- CREATE INDEX idx_users_email_verified ON users(email_verified);

-- ============================================
-- 설명:
-- ============================================
-- Users 테이블:
--   - profile_image: 여러 Provider 중 하나의 프로필 이미지 (우선순위: 네이버 > 구글)
--   - birth_date: 생년월일 (네이버에서만 제공)
--   - gender: 성별 (네이버에서만 제공)
--   - email_verified: 이메일 인증 여부 (구글에서 제공)
--
-- SocialUser 테이블:
--   - provider_data: JSON 형태로 모든 원본 OAuth2 응답 데이터 저장 (유연성 확보)
--   - provider_profile_image: 각 Provider별 프로필 이미지 (Users의 profile_image와 별도 보관)
--   - provider_name: 각 Provider별 이름 (구글은 given_name + family_name 조합 가능)
--   - provider_phone: 네이버 전화번호 (Users의 phone과 별도 보관)
--   - provider_age_range: 네이버 나이대 정보
--
-- 데이터 저장 전략:
--   - Users: 가장 완전한 정보 또는 최신 정보 우선
--   - SocialUser: 각 Provider별로 별도 레코드 저장 (한 사용자가 여러 Provider로 로그인 가능)

