-- 펫케어 요청: 일정 의미 및 예상 소요 시간 (2026)
-- 실행 전 백업 권장. 기존 행은 schedule_mode=FIXED 로 채워집니다.

ALTER TABLE carerequest
  ADD COLUMN schedule_mode VARCHAR(32) NOT NULL DEFAULT 'FIXED'
    COMMENT 'FIXED | FLEXIBLE_CHAT' AFTER date,
  ADD COLUMN estimated_duration_minutes INT NULL
    COMMENT '예상 돌봄 소요(분)' AFTER schedule_mode;
