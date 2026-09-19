-- 제안 상태에 EXPIRED(만료) 추가.
--
-- 제안을 받은 제공자가 수락도 거절도 하지 않으면 제안이 영원히 대기 상태로 남는다.
-- 요청자는 답을 기다리는 건지 잊힌 건지 알 수 없고, 제공자 목록에는 계속 "제안함"으로
-- 잠겨 있어 다시 보낼 수도 없다.
--
-- 왜 기존 값으로 안 되나:
--   REJECTED  = 제공자가 거절했다        (제공자가 한 행동)
--   WITHDRAWN = 요청자 사정으로 내렸다    (요청자가 한 행동)
--   EXPIRED   = 아무도 아무것도 안 했다   (시간이 지났다)
-- 셋을 뭉치면 화면 문구가 거짓이 된다. V20 에서 WITHDRAWN 을 따로 둔 것과 같은 이유다.

ALTER TABLE `careapplication`
    MODIFY COLUMN `status` enum('PENDING','ACCEPTED','REJECTED','WITHDRAWN','EXPIRED')
        NOT NULL DEFAULT 'PENDING';
