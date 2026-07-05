-- login_events 테이블 생성
-- DAU 정확한 집계를 위해 로그인 이벤트를 append-only로 저장한다.
-- Users.lastLoginAt 단일 컬럼 기반 집계(재로그인 시 누락)를 대체한다.
--
-- 적용:
-- mysql petory < backend/main/resources/sql/migration/login-events-create-table.sql

CREATE TABLE IF NOT EXISTS login_events (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    login_at   DATETIME(6)  NOT NULL,
    login_method VARCHAR(16) NOT NULL COMMENT 'LOCAL / GOOGLE / NAVER / KAKAO',
    PRIMARY KEY (id),
    CONSTRAINT fk_login_events_user
        FOREIGN KEY (user_id) REFERENCES users (idx)
        ON DELETE CASCADE,
    INDEX idx_login_events_user_login_at (user_id, login_at),
    INDEX idx_login_events_login_at (login_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
