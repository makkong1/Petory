-- locationservicereview soft delete 컬럼 추가
-- 배경:
-- - LocationServiceReview 엔티티와 JPQL/Native Query는 is_deleted, deleted_at 컬럼을 전제로 동작한다.
-- - 실제 로컬 스키마에 컬럼이 없으면 리뷰 조회/삭제/정렬(reviews sort)에서 Unknown column 오류가 발생한다.
-- 실행:
-- - 한 번만 실행하면 됨. 이미 컬럼/인덱스가 있으면 Duplicate column / Duplicate key name 오류가 나는데, 그때는 적용된 것으로 보면 됨.
-- - `ADD COLUMN IF NOT EXISTS` 는 MySQL 8.0.12+에서만 지원되어, 구버전/일부 환경에서는 1064가 난다.

ALTER TABLE locationservicereview
  ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN deleted_at DATETIME NULL;

CREATE INDEX idx_locationservicereview_service_deleted
  ON locationservicereview (service_idx, is_deleted);

CREATE INDEX idx_locationservicereview_user_deleted
  ON locationservicereview (user_idx, is_deleted);
