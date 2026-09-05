-- report.target_type 네이티브 enum에 CARE_REVIEW 추가.
-- ReportTargetType(Java)과 ReportService.validateTarget()엔 CARE_REVIEW가 이미 있었지만
-- V1 baseline의 DB enum엔 누락되어 있어, care_review 대상 신고 시 INSERT가
-- "Data truncated for column 'target_type'"로 실패했다.
ALTER TABLE `report`
  MODIFY COLUMN `target_type` enum('BOARD','COMMENT','MISSING_PET','PET_CARE_PROVIDER','CARE_REVIEW') NOT NULL;
