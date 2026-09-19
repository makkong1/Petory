-- 케어 계약의 확정 플래그를 채팅 참여자 행에서 걷어낸다.
--
-- 이 세 컬럼은 케어 계약 전용이었다. 다른 방 타입(모임·실종제보)은 쓰지 않는다.
-- 계약의 절반(확정)이 chat 에, 나머지 절반(이행 완료 확인)이 carerequest 에 나뉘어 있었고,
-- 그래서 두 가지가 따라왔다.
--   - 방을 나가거나 지우면 계약 동의가 같이 사라진다
--   - care 도메인만 봐서는 계약이 어디까지 갔는지 알 수 없다
--
-- 계약을 제안/수락으로 바꾸면서(V18) 동의가 한 번뿐이 됐고, 그 한 번은 careapplication 의
-- 상태(PENDING -> ACCEPTED/REJECTED)가 그대로 표현한다. 대칭 확정이 필요로 하던
-- "낡은 동의 무효화"도 같이 사라져서 confirmed_offered_coins 의 쓸 자리가 없다.
--
-- V15 가 confirmed_offered_coins 를 추가한 이유(시각 비교 대신 금액 자체를 기록)는 그대로
-- 유효하다 — 그 불변식은 careapplication.offered_coins 가 이어받는다. 동의가 하나뿐이라
-- 무효화 없이 수락 시점 대조 한 번으로 성립한다.

ALTER TABLE `conversationparticipant`
    DROP COLUMN `confirmed_offered_coins`,
    DROP COLUMN `deal_confirmed_at`,
    DROP COLUMN `deal_confirmed`;
