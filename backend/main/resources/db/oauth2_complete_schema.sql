-- ============================================
-- OAuth2 소셜 로그인 완전한 테이블 스키마
-- ============================================

-- Users 테이블 (OAuth2 필드 포함)
CREATE TABLE IF NOT EXISTS Users (
    idx BIGINT AUTO_INCREMENT PRIMARY KEY,
    id VARCHAR(50) NOT NULL UNIQUE COMMENT '로그인용 아이디',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '사용자 이름',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '이메일',
    password VARCHAR(255) NOT NULL COMMENT '비밀번호 (소셜 로그인은 UUID)',
    role ENUM('USER','SERVICE_PROVIDER','ADMIN','MASTER') DEFAULT 'USER' COMMENT '사용자 역할',
    phone VARCHAR(20) COMMENT '전화번호',
    location VARCHAR(255) COMMENT '위치 정보',
    pet_info TEXT COMMENT '반려동물 정보',
    
    -- OAuth2 관련 필드 추가
    profile_image VARCHAR(500) NULL COMMENT '프로필 이미지 URL (구글 picture, 네이버 profile_image)',
    birth_date VARCHAR(20) NULL COMMENT '생년월일 (네이버: birthyear + birthday 조합, 형식: YYYY-MM-DD)',
    gender VARCHAR(10) NULL COMMENT '성별 (네이버: M/F, 구글: 제공 안 함)',
    email_verified BOOLEAN DEFAULT FALSE COMMENT '이메일 인증 여부 (구글: email_verified, 네이버: 기본 true)',
    
    -- 인증 관련 필드
    last_login_at DATETIME COMMENT '마지막 로그인 시간',
    refresh_token VARCHAR(255) COMMENT '리프레시 토큰',
    refresh_expiration DATETIME COMMENT '리프레시 토큰 만료 시간',
    
    -- 제재 관련 필드
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/BANNED/SUSPENDED 등',
    warning_count INT NOT NULL DEFAULT 0 COMMENT '경고 횟수',
    suspended_until DATETIME NULL COMMENT '정지 종료 일시',
    
    -- 소프트 삭제 관련 필드
    is_deleted TINYINT(1) DEFAULT 0 COMMENT '삭제 여부',
    deleted_at DATETIME NULL COMMENT '삭제 일시',
    
    -- 타임스탬프
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    
    -- 인덱스
    INDEX idx_users_email (email),
    INDEX idx_users_status (status),
    INDEX idx_users_email_verified (email_verified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 테이블';

-- SocialUser 테이블 (OAuth2 필드 포함)
CREATE TABLE IF NOT EXISTS SocialUser (
    idx BIGINT AUTO_INCREMENT PRIMARY KEY,
    users_idx BIGINT NOT NULL COMMENT 'Users 테이블 외래키',
    provider ENUM('GOOGLE','NAVER','KAKAO','GITHUB') NOT NULL COMMENT '소셜 로그인 제공자 (NAVER 추가)',
    provider_id VARCHAR(100) NOT NULL COMMENT 'Provider별 사용자 고유 ID',
    
    -- Provider별 상세 정보 필드 추가
    provider_data TEXT NULL COMMENT 'Provider별 원본 데이터 JSON (모든 OAuth2 응답 데이터 저장)',
    provider_profile_image VARCHAR(500) NULL COMMENT 'Provider별 프로필 이미지 URL',
    provider_name VARCHAR(100) NULL COMMENT 'Provider별 이름 (구글: given_name + family_name, 네이버: name)',
    provider_phone VARCHAR(50) NULL COMMENT 'Provider별 전화번호 (네이버: mobile 또는 mobile_e164)',
    provider_age_range VARCHAR(20) NULL COMMENT 'Provider별 나이대 (네이버: 20-29 형식)',
    
    -- 타임스탬프
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    
    -- 외래키 제약조건
    FOREIGN KEY (users_idx) REFERENCES Users(idx) ON DELETE CASCADE,
    
    -- 인덱스
    UNIQUE KEY uk_socialuser_provider_providerid (provider, provider_id),
    INDEX idx_socialuser_users_idx (users_idx),
    INDEX idx_socialuser_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소셜 로그인 사용자 테이블';

-- ============================================
-- 기존 테이블에 필드 추가하는 ALTER 문 (이미 테이블이 있는 경우)
-- ============================================

-- Users 테이블에 OAuth2 필드 추가
ALTER TABLE Users
ADD COLUMN IF NOT EXISTS profile_image VARCHAR(500) NULL COMMENT '프로필 이미지 URL (구글 picture, 네이버 profile_image)',
ADD COLUMN IF NOT EXISTS birth_date VARCHAR(20) NULL COMMENT '생년월일 (네이버: birthyear + birthday 조합, 형식: YYYY-MM-DD)',
ADD COLUMN IF NOT EXISTS gender VARCHAR(10) NULL COMMENT '성별 (네이버: M/F, 구글: 제공 안 함)',
ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE COMMENT '이메일 인증 여부 (구글: email_verified, 네이버: 기본 true)';

-- SocialUser 테이블에 Provider별 상세 정보 필드 추가
ALTER TABLE SocialUser
ADD COLUMN IF NOT EXISTS provider_data TEXT NULL COMMENT 'Provider별 원본 데이터 JSON (모든 OAuth2 응답 데이터 저장)',
ADD COLUMN IF NOT EXISTS provider_profile_image VARCHAR(500) NULL COMMENT 'Provider별 프로필 이미지 URL',
ADD COLUMN IF NOT EXISTS provider_name VARCHAR(100) NULL COMMENT 'Provider별 이름 (구글: given_name + family_name, 네이버: name)',
ADD COLUMN IF NOT EXISTS provider_phone VARCHAR(50) NULL COMMENT 'Provider별 전화번호 (네이버: mobile 또는 mobile_e164)',
ADD COLUMN IF NOT EXISTS provider_age_range VARCHAR(20) NULL COMMENT 'Provider별 나이대 (네이버: 20-29 형식)';

-- SocialUser 테이블의 provider ENUM에 NAVER 추가 (MySQL 8.0 이상)
-- 주의: ENUM 수정은 기존 데이터에 영향을 줄 수 있으므로 신중하게 실행
-- ALTER TABLE SocialUser MODIFY COLUMN provider ENUM('GOOGLE','NAVER','KAKAO','GITHUB') NOT NULL;

-- ============================================
-- 데이터 저장 전략 설명
-- ============================================
-- 
-- Users 테이블:
--   - profile_image: 여러 Provider 중 하나의 프로필 이미지 (우선순위: 네이버 > 구글)
--   - birth_date: 생년월일 (네이버에서만 제공, 형식: "1998-04-24")
--   - gender: 성별 (네이버에서만 제공, "M" 또는 "F")
--   - email_verified: 이메일 인증 여부 (구글에서 제공, 네이버는 기본 true로 간주)
--
-- SocialUser 테이블:
--   - provider_data: JSON 형태로 모든 원본 OAuth2 응답 데이터 저장 (유연성 확보)
--     예: {"sub":"114640479815517509032","name":"박영범","email":"ren42435@gmail.com",...}
--   - provider_profile_image: 각 Provider별 프로필 이미지 (Users의 profile_image와 별도 보관)
--   - provider_name: 각 Provider별 이름 (구글은 given_name + family_name 조합 가능)
--   - provider_phone: 네이버 전화번호 (Users의 phone과 별도 보관)
--   - provider_age_range: 네이버 나이대 정보 ("20-29" 형식)
--
-- 데이터 병합 전략:
--   - 한 사용자가 여러 Provider로 로그인한 경우:
--     * Users 테이블: 가장 완전한 정보 또는 최신 정보 우선
--     * SocialUser 테이블: 각 Provider별로 별도 레코드 저장
--
-- 예시:
--   Users (1개 레코드)
--   - email: ren42435@gmail.com
--   - username: 박영범
--   - phone: 010-8494-8284 (네이버에서)
--   - profile_image: https://... (구글에서)
--   - gender: M (네이버에서)
--   - birth_date: 1998-04-24 (네이버에서)
--
--   SocialUser (2개 레코드)
--   1. provider=GOOGLE, provider_id=114640479815517509032
--      - provider_data: {전체 JSON}
--      - provider_profile_image: 구글 picture
--   2. provider=NAVER, provider_id=VoBE7rq0W7Ixhypl4oRKA65Du6TtMM7sIWAbk_Vc0Fo
--      - provider_data: {전체 JSON}
--      - provider_phone: 010-8494-8284
--      - provider_age_range: 20-29

