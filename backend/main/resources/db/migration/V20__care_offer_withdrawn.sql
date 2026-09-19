-- 제안 상태에 WITHDRAWN(철회) 추가.
--
-- 지금까지 제안이 끝나는 길은 REJECTED 하나뿐이었는데, 그건 "제공자가 거절했다"는 뜻이다.
-- 그런데 제공자 의사와 무관하게 제안이 끝나는 경우가 셋 있다.
--   - 요청자가 제시 금액을 바꿨다 (제공자가 본 금액과 성립할 금액이 달라진다)
--   - 요청이 취소됐다 (예정일 경과 자동 취소 포함)
--   - 요청이 삭제됐다
-- 이걸 REJECTED 로 적으면 상태가 거짓말을 한다 — 제공자 화면에 "거절하셨습니다"로 남는다.
--
-- 상태가 사실을 말하게 하려고 값을 하나 더 둔다. 화면 문구도 갈린다:
--   REJECTED  = 내가 거절했다
--   WITHDRAWN = 요청자 사정으로 제안이 내려갔다 (알림으로 이유를 함께 보낸다)

ALTER TABLE `careapplication`
    MODIFY COLUMN `status` enum('PENDING','ACCEPTED','REJECTED','WITHDRAWN')
        NOT NULL DEFAULT 'PENDING';
