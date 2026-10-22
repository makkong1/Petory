-- 동기화 skip 사유를 셋으로 쪼갠다.
--
-- 지금은 skip 사유 세 개가 skipped 한 칸에 합쳐져 기록된다. 그래서 이력만 보면
-- 동기화가 멱등하게 잘 돈 것인지, 원본·매핑이 깨져 전량 거부된 것인지 구분할 수 없다.
-- 실제로 기록된 6회가 전부 inserted=0 updated=0 skipped=70650 인데,
-- 이 수치로는 어느 쪽인지 말할 수 없었다 (API 를 직접 호출해 보고서야 66% 가
-- 원본의 완전중복 행이라 정상이라는 것을 확인했다).
--
--   skipped_invalid   = 시설명·주소가 없어 쓸 수 없는 행      → 높으면 원본·매핑 고장
--   skipped_duplicate = 같은 실행 안에서 이미 본 행           → 높으면 원본 품질 문제
--   skipped_unchanged = 이미 있고 내용도 같은 행              → 높으면 멱등 정상
--
-- NULL 을 허용하는 이유: 기존 6행은 쪼개진 값을 모른다. 0 으로 채우면
-- 「중복 0건이었다」는 거짓이 기록된다. NULL = 계측 이전.
-- skipped 는 합계로 그대로 둔다 (기존 행·기존 조회 호환).

ALTER TABLE `location_sync_log`
    ADD COLUMN `skipped_invalid`   INT NULL AFTER `skipped`,
    ADD COLUMN `skipped_duplicate` INT NULL AFTER `skipped_invalid`,
    ADD COLUMN `skipped_unchanged` INT NULL AFTER `skipped_duplicate`;
