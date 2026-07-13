-- =============================================================================
-- Petory 데이터 정합성 검증
-- =============================================================================
-- 실행:
--   mysql -h127.0.0.1 -P3306 -uroot -p petory < scripts/seed/verify-data-integrity.sql
--
-- 모든 항목이 0 이어야 정상이다. 하나라도 0 이 아니면 데이터가 어긋난 것이다.
--
-- 시드 직후뿐 아니라 **주기적으로** 돌려서 반정규화 카운터 드리프트를 잡는 용도로도 쓴다.
-- (2026-07-13: 운영 중 드리프트를 탐지할 장치가 없어서, 카운터가 어긋난 걸
--  아무도 모르고 있었다. docs/analysis/entity-schema/evidence/ 참고)
-- =============================================================================

SELECT '── 반정규화 카운터 (원본 집계와 대조) ──' AS '';

SELECT 'board.like_count' AS 항목, COUNT(*) AS 불일치 FROM board b
LEFT JOIN (SELECT board_idx, COUNT(*) c FROM board_reaction WHERE reaction_type='LIKE' GROUP BY board_idx) r
  ON r.board_idx = b.idx
WHERE b.like_count <> IFNULL(r.c, 0)

UNION ALL SELECT 'board.dislike_count', COUNT(*) FROM board b
LEFT JOIN (SELECT board_idx, COUNT(*) c FROM board_reaction WHERE reaction_type='DISLIKE' GROUP BY board_idx) r
  ON r.board_idx = b.idx
WHERE b.dislike_count <> IFNULL(r.c, 0)

UNION ALL SELECT 'board.comment_count', COUNT(*) FROM board b
LEFT JOIN (SELECT board_idx, COUNT(*) c FROM comment WHERE is_deleted = 0 GROUP BY board_idx) cm
  ON cm.board_idx = b.idx
WHERE b.comment_count <> IFNULL(cm.c, 0)

UNION ALL SELECT 'board.view_count', COUNT(*) FROM board b
LEFT JOIN (SELECT board_id, COUNT(*) c FROM board_view_log GROUP BY board_id) v ON v.board_id = b.idx
WHERE b.view_count <> IFNULL(v.c, 0)

UNION ALL SELECT 'meetup.current_participants', COUNT(*) FROM meetup m
LEFT JOIN (SELECT meetup_idx, COUNT(*) c FROM meetupparticipants GROUP BY meetup_idx) p
  ON p.meetup_idx = m.idx
WHERE m.current_participants <> IFNULL(p.c, 0)

UNION ALL SELECT 'locationservice.review_count', COUNT(*) FROM locationservice ls
LEFT JOIN (SELECT service_idx, COUNT(*) c FROM locationservicereview WHERE is_deleted=0 GROUP BY service_idx) r
  ON r.service_idx = ls.idx
WHERE ls.review_count <> IFNULL(r.c, 0)

UNION ALL SELECT 'locationservice.rating', COUNT(*) FROM locationservice ls
LEFT JOIN (SELECT service_idx, ROUND(AVG(rating),1) avg_r FROM locationservicereview WHERE is_deleted=0
           GROUP BY service_idx) r ON r.service_idx = ls.idx
WHERE ls.rating <> IFNULL(r.avg_r, 0)

UNION ALL SELECT 'users.pet_coin_balance', COUNT(*) FROM users u
JOIN (SELECT p.user_idx, p.balance_after AS last_bal FROM pet_coin_transaction p
      JOIN (SELECT user_idx, MAX(idx) mx FROM pet_coin_transaction GROUP BY user_idx) m
        ON m.user_idx = p.user_idx AND m.mx = p.idx) t ON t.user_idx = u.idx
WHERE u.pet_coin_balance <> t.last_bal;


SELECT '── 코인 원장: 러닝 밸런스가 이어지는가 ──' AS '';

-- 각 거래의 balance_after 가 balance_before ± amount 와 맞아야 한다
SELECT 'balance_before/after 계산 오류' AS 항목, COUNT(*) AS 불일치
FROM pet_coin_transaction
WHERE balance_after <> balance_before + IF(transaction_type = 'DEDUCT', -amount, amount)

UNION ALL
-- 잔액이 음수인 유저 (있으면 안 된다)
SELECT '음수 잔액 유저', COUNT(*) FROM users WHERE pet_coin_balance < 0;


SELECT '── 도메인 불변식 ──' AS '';

SELECT '펫 없는 유저(시드)' AS 항목, COUNT(*) AS 위반 FROM users u
LEFT JOIN pets p ON p.user_idx = u.idx
WHERE u.email LIKE 'seed_user_%' AND p.idx IS NULL

UNION ALL
-- 거래 확정된 케어 요청(OPEN 아님)에는 에스크로가 있어야 한다
SELECT '에스크로 없는 확정 케어요청', COUNT(*) FROM carerequest c
LEFT JOIN pet_coin_escrow e ON e.care_request_idx = c.idx
WHERE c.status <> 'OPEN' AND e.idx IS NULL

UNION ALL
-- 반대로 OPEN 요청에는 에스크로가 없어야 한다 (코인이 미리 묶이면 안 됨)
SELECT 'OPEN인데 에스크로 있음', COUNT(*) FROM carerequest c
JOIN pet_coin_escrow e ON e.care_request_idx = c.idx
WHERE c.status = 'OPEN'

UNION ALL
-- 에스크로 상태가 케어 요청 상태와 어긋나면 안 된다
SELECT '에스크로-요청 상태 불일치', COUNT(*) FROM pet_coin_escrow e
JOIN carerequest c ON c.idx = e.care_request_idx
WHERE (c.status = 'COMPLETED' AND e.status <> 'RELEASED')
   OR (c.status = 'CANCELLED' AND e.status <> 'REFUNDED')
   OR (c.status = 'IN_PROGRESS' AND e.status <> 'HOLD')

UNION ALL
-- 모임의 주최자는 참여자여야 한다
SELECT '주최자가 참여자가 아닌 모임', COUNT(*) FROM meetup m
LEFT JOIN meetupparticipants p ON p.meetup_idx = m.idx AND p.user_idx = m.organizer_idx
WHERE p.meetup_idx IS NULL

UNION ALL
-- 참여자가 정원을 초과하면 안 된다
SELECT '정원 초과 모임', COUNT(*) FROM meetup WHERE current_participants > max_participants

UNION ALL
-- 케어 채팅방에는 요청자와 제공자 2명이 있어야 한다
SELECT '참여자 2명이 아닌 케어 채팅방', COUNT(*) FROM (
  SELECT cv.idx, COUNT(cp.idx) c FROM conversation cv
  LEFT JOIN conversationparticipant cp ON cp.conversation_idx = cv.idx
  WHERE cv.conversation_type = 'CARE_REQUEST' GROUP BY cv.idx HAVING c <> 2) x

UNION ALL
-- 수락된 지원은 요청당 최대 1건
SELECT 'ACCEPTED 지원이 2건 이상인 요청', COUNT(*) FROM (
  SELECT care_request_idx FROM careapplication WHERE status='ACCEPTED'
  GROUP BY care_request_idx HAVING COUNT(*) > 1) x;


SELECT '── 참조 무결성 (고아 행) ──' AS '';

SELECT 'comment → users' AS 항목, COUNT(*) AS 고아 FROM comment c
  LEFT JOIN users u ON u.idx = c.user_idx WHERE u.idx IS NULL
UNION ALL SELECT 'comment → board', COUNT(*) FROM comment c
  LEFT JOIN board b ON b.idx = c.board_idx WHERE b.idx IS NULL
UNION ALL SELECT 'board_reaction → board', COUNT(*) FROM board_reaction r
  LEFT JOIN board b ON b.idx = r.board_idx WHERE b.idx IS NULL
UNION ALL SELECT 'meetupparticipants → meetup', COUNT(*) FROM meetupparticipants p
  LEFT JOIN meetup m ON m.idx = p.meetup_idx WHERE m.idx IS NULL
UNION ALL SELECT 'chatmessage → conversation', COUNT(*) FROM chatmessage cm
  LEFT JOIN conversation cv ON cv.idx = cm.conversation_idx WHERE cv.idx IS NULL
UNION ALL SELECT 'carerequest → pets', COUNT(*) FROM carerequest c
  LEFT JOIN pets p ON p.idx = c.pet_idx WHERE c.pet_idx IS NOT NULL AND p.idx IS NULL
UNION ALL SELECT 'pet_coin_escrow → carerequest', COUNT(*) FROM pet_coin_escrow e
  LEFT JOIN carerequest c ON c.idx = e.care_request_idx WHERE c.idx IS NULL;
