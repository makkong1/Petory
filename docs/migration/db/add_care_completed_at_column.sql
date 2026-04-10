-- CareRequest 테이블에 completed_at 컬럼 추가
-- 통계 집계용 케어 완료 시각 (CareRequest.date(케어 예정일)와 구분)

ALTER TABLE carerequest
ADD COLUMN completed_at DATETIME NULL COMMENT '케어 완료 시각 (통계 집계용)';
