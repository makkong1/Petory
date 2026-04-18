-- 통계 도메인 재설계 마이그레이션
-- 목적: Daily/Weekly/Monthly 3단계 집계 구조 + 비율 지표/매출/사용자 세그먼트 추가
-- 실행 순서: 1) daily_statistics ALTER → 2) weekly_statistics CREATE → 3) monthly_statistics CREATE

-- ============================================================
-- 1. daily_statistics 테이블 변경
--    - Integer → BIGINT (오버플로우 방지)
--    - 신규 컬럼 추가
-- 실행 팁: 클라이언트가 여러 문장을 한 번에 못 돌리면 아래 ALTER를 각각 실행.
-- ============================================================

-- 1a) 기존 정수형 컬럼 → BIGINT
ALTER TABLE dailystatistics
    MODIFY COLUMN new_users           BIGINT NOT NULL DEFAULT 0 COMMENT '신규 가입자',
    MODIFY COLUMN active_users        BIGINT NOT NULL DEFAULT 0 COMMENT 'DAU',
    MODIFY COLUMN new_care_requests BIGINT NOT NULL DEFAULT 0 COMMENT '케어 요청 수',
    MODIFY COLUMN completed_cares     BIGINT NOT NULL DEFAULT 0 COMMENT '케어 완료 수',
    MODIFY COLUMN new_posts           BIGINT NOT NULL DEFAULT 0 COMMENT '신규 게시글',
    MODIFY COLUMN new_meetups         BIGINT NOT NULL DEFAULT 0 COMMENT '신규 모임',
    MODIFY COLUMN meetup_participants BIGINT NOT NULL DEFAULT 0 COMMENT '모임 참여자 수',
    MODIFY COLUMN new_reports         BIGINT NOT NULL DEFAULT 0 COMMENT '신고 접수';

-- 1b) 신규 컬럼 (JPA로 이미 만들어졌으면 Duplicate column 에러 — 해당 ADD 줄만 건너뛰면 됨)
ALTER TABLE dailystatistics
    ADD COLUMN new_providers         BIGINT        NOT NULL DEFAULT 0 COMMENT '신규 서비스 제공자' AFTER active_users,
    ADD COLUMN cancelled_cares       BIGINT        NOT NULL DEFAULT 0 COMMENT '케어 취소 수' AFTER completed_cares,
    ADD COLUMN care_completion_rate  DECIMAL(5,2)  NOT NULL DEFAULT 0 COMMENT '케어 완료율 pct' AFTER cancelled_cares,
    ADD COLUMN transaction_count     BIGINT        NOT NULL DEFAULT 0 COMMENT '결제 건수' AFTER total_revenue,
    ADD COLUMN avg_transaction       DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '평균 거래금액' AFTER transaction_count,
    ADD COLUMN resolved_reports      BIGINT        NOT NULL DEFAULT 0 COMMENT '신고 처리 수' AFTER new_reports;

-- ============================================================
-- 2. weekly_statistics 테이블 생성
-- ============================================================

CREATE TABLE IF NOT EXISTS weekly_statistics (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year                 INT           NOT NULL                        COMMENT 'ISO 연도',
    week_number          INT           NOT NULL                        COMMENT 'ISO 주차 (1~53)',
    start_date           DATE          NOT NULL                        COMMENT '해당 주 월요일',
    end_date             DATE          NOT NULL                        COMMENT '해당 주 일요일',

    -- 사용자
    new_users            BIGINT        NOT NULL DEFAULT 0,
    active_users         BIGINT        NOT NULL DEFAULT 0             COMMENT 'WAU',
    new_providers        BIGINT        NOT NULL DEFAULT 0,
    weekly_retention_rate DECIMAL(5,2) NOT NULL DEFAULT 0             COMMENT '주간 재방문율',

    -- 케어
    new_care_requests    BIGINT        NOT NULL DEFAULT 0,
    completed_cares      BIGINT        NOT NULL DEFAULT 0,
    cancelled_cares      BIGINT        NOT NULL DEFAULT 0,
    care_completion_rate DECIMAL(5,2)  NOT NULL DEFAULT 0,

    -- 결제
    total_revenue        DECIMAL(15,2) NOT NULL DEFAULT 0,
    transaction_count    BIGINT        NOT NULL DEFAULT 0,
    avg_transaction      DECIMAL(15,2) NOT NULL DEFAULT 0,

    -- 커뮤니티
    new_posts            BIGINT        NOT NULL DEFAULT 0,
    new_meetups          BIGINT        NOT NULL DEFAULT 0,
    meetup_participants  BIGINT        NOT NULL DEFAULT 0,

    -- 운영
    new_reports          BIGINT        NOT NULL DEFAULT 0,
    resolved_reports     BIGINT        NOT NULL DEFAULT 0,

    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_weekly_year_week (year, week_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='주간 통계 (무기한 보관)';

-- ============================================================
-- 3. monthly_statistics 테이블 생성
-- ============================================================

CREATE TABLE IF NOT EXISTS monthly_statistics (
    id                      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year                    INT           NOT NULL                    COMMENT '연도',
    month                   INT           NOT NULL                    COMMENT '월 (1~12)',

    -- 사용자
    new_users               BIGINT        NOT NULL DEFAULT 0,
    active_users            BIGINT        NOT NULL DEFAULT 0          COMMENT 'MAU',
    new_providers           BIGINT        NOT NULL DEFAULT 0,
    monthly_retention_rate  DECIMAL(5,2)  NOT NULL DEFAULT 0          COMMENT '월간 재방문율',
    churn_rate              DECIMAL(5,2)  NOT NULL DEFAULT 0          COMMENT '이탈율',

    -- 케어
    new_care_requests       BIGINT        NOT NULL DEFAULT 0,
    completed_cares         BIGINT        NOT NULL DEFAULT 0,
    cancelled_cares         BIGINT        NOT NULL DEFAULT 0,
    care_completion_rate    DECIMAL(5,2)  NOT NULL DEFAULT 0,

    -- 결제
    total_revenue           DECIMAL(15,2) NOT NULL DEFAULT 0,
    transaction_count       BIGINT        NOT NULL DEFAULT 0,
    avg_transaction         DECIMAL(15,2) NOT NULL DEFAULT 0,

    -- 커뮤니티
    new_posts               BIGINT        NOT NULL DEFAULT 0,
    new_meetups             BIGINT        NOT NULL DEFAULT 0,
    meetup_participants     BIGINT        NOT NULL DEFAULT 0,

    -- 운영
    new_reports             BIGINT        NOT NULL DEFAULT 0,
    resolved_reports        BIGINT        NOT NULL DEFAULT 0,

    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_monthly_year_month (year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='월간 통계 (무기한 보관)';