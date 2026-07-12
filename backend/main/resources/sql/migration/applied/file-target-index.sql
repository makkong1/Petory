-- file 테이블 (target_type, target_idx) 복합 인덱스 추가
-- 배경: Care/MissingPet N+1 재검증(2026-07-12) 중 발견.
--   AttachmentFileService.getAttachments()/getAttachmentsBatch()가 공통으로 쓰는
--   WHERE target_type=? AND target_idx=?(또는 IN) 조건에 대응하는 인덱스가 없어서
--   개별조회든 배치조회든 매번 file 테이블 전체를 스캔하고 있었다.
--   (docs/refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md §3 참고)
CREATE INDEX idx_file_target ON file (target_type, target_idx);
