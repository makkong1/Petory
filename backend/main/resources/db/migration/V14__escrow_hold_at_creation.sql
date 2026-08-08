-- 에스크로를 "거래 확정 시"가 아니라 "요청 등록 시"에 잡도록 스키마를 연다.
--
-- 배경: 왜 옮기나
--   createCareRequest 에는 이미 잔액 "확인"이 있었다.
--       if (user.getPetCoinBalance() < dto.getOfferedCoins()) throw insufficientBalance();
--   등록 시점에 지급 능력을 보겠다는 의도는 처음부터 있었던 셈인데, 확인만 하고 잡지는
--   않으니 그 사이 잔액을 다른 데 쓰면 확정 순간에 깨진다(TOCTOU). 제공자는 신청하고
--   채팅까지 마친 뒤에야 그 사실을 알게 된다.
--   충전형 코인이라 등록 시 차감은 새로 결제를 받는 게 아니라 플랫폼 안에서
--   잔액 -> 에스크로로 옮기는 것뿐이라, 외부 결제 서비스와 달리 이 시점 이동이 가능하다.
--
-- 1) provider_idx 를 NULL 허용으로
--   등록 시점에는 제공자가 정해지지 않았다. 확정 시 채운다.
--   그래서 provider_idx IS NULL 은 "아직 상대가 정해지지 않은 보관"이라는 뜻이 되고,
--   확정 여부를 판별하는 신호로도 쓴다(컬럼 하나가 두 몫).
--
-- 2) carerequest.offered_coins_updated_at
--   금액은 OPEN 인 동안 수정할 수 있다. 그런데 확정은 요청자/제공자가 각자 따로 누르므로,
--   한쪽이 5,000 에 동의한 뒤 금액이 3,000 으로 바뀌고 다른 쪽이 3,000 에 동의하면
--   서로 다른 금액에 동의한 채 계약이 성립한다.
--   확정 시 "내 동의가 금액 변경보다 이전인가"를 이 시각으로 판별해 낡은 동의를 무효화한다.
--   (chat 이 care 를 읽는 방향은 유지된다 — care 가 chat 의 확정 플래그를 건드리면 순환이 된다.)

ALTER TABLE `pet_coin_escrow`
    MODIFY COLUMN `provider_idx` bigint DEFAULT NULL
        COMMENT '제공자 ID (거래 확정 시 배정. NULL = 아직 상대 미정)';

ALTER TABLE `carerequest`
    ADD COLUMN `offered_coins_updated_at` datetime DEFAULT NULL
        COMMENT '제시 금액이 마지막으로 바뀐 시각 (NULL = 등록 이후 변경 없음)';
