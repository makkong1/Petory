-- file.file_path 에 잘못 저장된 'uploads/' 접두사 제거
-- 증상: GET /api/uploads/file?path=uploads/missing-pet/... → 404
--       FileStorageService.loadAsResource 에서 uploadLocation/uploads/... (이중 경로) 탐색
--
-- 원인: syncSingleAttachment 호출 시 rawPath가 이미 'uploads/' 로 시작하는 경우
--       extractRelativePath 가 '/' 없는 접두사를 처리하지 못해 그대로 저장됨
--
-- 적용: mysql petory < backend/main/resources/sql/migration/file-path-uploads-prefix-fix.sql

UPDATE file
SET file_path = SUBSTRING(file_path, LENGTH('uploads/') + 1)
WHERE file_path LIKE 'uploads/%'
  AND file_path NOT LIKE 'http%';
