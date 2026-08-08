-- 케어 완료를 양쪽이 확인해야 정산되도록, 확인 시각 두 개를 둔다.
--
-- 배경: 왜 필요한가
--   updateStatus 는 요청자 "또는" 승인된 제공자 아무나 호출할 수 있었고,
--   COMPLETED 로 바뀌는 순간 에스크로가 제공자에게 지급됐다.
--   즉 제공자가 혼자 완료를 눌러 요청자 동의 없이 돈을 가져갈 수 있었다.
--   (같은 경로를 스케줄러도 타서, 예정일만 지나면 자동 지급됐다 — 그쪽은 앞선 커밋에서 끊었다.)
--
-- 왜 boolean 이 아니라 datetime 인가
--   "확인했는가"만이 아니라 "언제 확인했는가"가 분쟁 시 필요한 정보다.
--   NULL = 미확인 이므로 boolean 이 담던 정보는 그대로 담긴다.
--
-- 기존 행 처리
--   이미 COMPLETED 인 과거 데이터는 두 컬럼이 NULL 로 남는다. 소급해서 채우지 않는다 —
--   실제로 양쪽이 확인한 적이 없기 때문이다. 완료 여부는 status 가 계속 정본이고,
--   이 두 컬럼은 "이 정산이 양쪽 확인을 거쳤는지"를 구분하는 용도다.

ALTER TABLE `carerequest`
    ADD COLUMN `requester_completed_at` datetime DEFAULT NULL
        COMMENT '요청자가 이행 완료를 확인한 시각 (NULL = 미확인)',
    ADD COLUMN `provider_completed_at` datetime DEFAULT NULL
        COMMENT '제공자가 이행 완료를 확인한 시각 (NULL = 미확인)';
