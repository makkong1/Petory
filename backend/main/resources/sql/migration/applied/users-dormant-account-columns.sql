-- 휴면 계정 기능: 1년 미로그인 사용자를 배치로 휴면 전환하기 위한 컬럼.
-- UserStatus(ACTIVE/SUSPENDED/BANNED, 제재 전용)와 독립적인 별도 상태.
ALTER TABLE users
    ADD COLUMN is_dormant TINYINT(1) DEFAULT 0 AFTER deleted_at,
    ADD COLUMN dormant_at DATETIME NULL AFTER is_dormant;
