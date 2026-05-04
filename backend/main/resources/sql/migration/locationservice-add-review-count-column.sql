-- locationservice.review_count 캐시 컬럼 추가 및 백필
-- 배경:
-- - reviews 정렬이 반경 검색 ORDER BY에서 상관 서브쿼리로 리뷰 수를 직접 세면 후보 행마다 비용이 반복된다.
-- - locationservice.review_count 캐시 컬럼으로 바꾸면 reviews 정렬을 단순화할 수 있다.
-- 집계 계약:
-- - 활성 리뷰만 센다: locationservicereview.is_deleted IS NULL OR is_deleted = 0 (앱·Native Query와 동일)
-- 실행:
-- - 한 번만 실행하면 됨. review_count 컬럼이 이미 있으면 Duplicate column 오류는 적용된 것으로 보면 됨.
-- - 백필 UPDATE는 locationservicereview.is_deleted 가 있어야 한다. 없으면 `locationservicereview-add-soft-delete-columns.sql` 먼저 적용.
-- - is_deleted 없이 긴급 백필만 할 경우: 아래 UPDATE의 서브쿼리에서 WHERE 절을 제거한 뒤 실행(이후 soft delete 반영 시 앱/재백필로 맞출 것).

ALTER TABLE locationservice
  ADD COLUMN review_count INT NOT NULL DEFAULT 0 COMMENT '활성 리뷰 수 캐시 (soft delete 제외)';

UPDATE locationservice ls
LEFT JOIN (
  SELECT r.service_idx, COUNT(*) AS review_count
  FROM locationservicereview r
  WHERE r.is_deleted IS NULL OR r.is_deleted = 0
  GROUP BY r.service_idx
) rc ON rc.service_idx = ls.idx
SET ls.review_count = COALESCE(rc.review_count, 0);
