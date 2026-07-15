-- =============================================================================
-- Petory 개발/성능측정용 시드 데이터
-- =============================================================================
--
-- ⚠️ 이 파일은 Flyway 경로(backend/main/resources/db/migration/)에 두면 안 된다.
--    거기 두면 운영 DB에서도 더미가 생성된다.
--
-- 실행:
--   mysql -h127.0.0.1 -P3306 -uroot -p petory < scripts/seed/seed-dev-data.sql
--
-- 보존: locationservice(공공데이터), wowong123@naver.com(MASTER 계정)
-- 삭제: 그 외 전부
--
-- -----------------------------------------------------------------------------
-- 설계 원칙: 반정규화 카운터를 "직접 넣지 않는다"
-- -----------------------------------------------------------------------------
-- like_count, comment_count, current_participants, view_count, pet_coin_balance,
-- rating, review_count 는 전부 §6 에서 **실제 자식 행을 집계해서 유도**한다.
-- 값을 임의로 채워 넣지 않으므로 카운터 불일치가 구조적으로 발생할 수 없다.
--
-- (2026-07-13 이전 더미가 정확히 이 원칙을 어겨서, view_count 총합 515만인데
--  board_view_log 는 17행뿐인 상태가 되어 있었다.)
--
-- -----------------------------------------------------------------------------
-- 도메인 상태 기계 (코드에서 확인한 실제 규칙)
-- -----------------------------------------------------------------------------
--   CareRequest(OPEN) → CareApplication(PENDING) → 수락(ACCEPTED)
--     → 채팅방 생성(CARE_REQUEST 타입, related=CARE_APPLICATION)
--     → 양쪽 거래확정(deal_confirmed) → 에스크로 HOLD + 요청자 코인 DEDUCT
--     → 완료: 에스크로 RELEASED + 제공자 코인 PAYOUT
--     → 취소: 에스크로 REFUNDED + 요청자 코인 REFUND
--
--   즉 에스크로는 "케어 요청 시점"이 아니라 "거래 확정 시점"에 생긴다.
--   OPEN 상태 요청에는 에스크로도 채팅방도 없는 것이 정상이다.
-- =============================================================================

SET SESSION cte_max_recursion_depth = 1000000;
SET SESSION sql_mode = '';
SET autocommit = 1;

-- ── 규모 (여기만 바꾸면 됨) ──────────────────────────────────────────────────
SET @USERS    = 10000;   -- 유저
SET @BOARDS   = 50000;   -- 게시글 (인덱스/딥페이징 측정이 유의미해지는 하한선)
SET @COMMENTS = 150000;  -- 댓글
SET @MEETUPS  = 5000;    -- 모임
SET @CARE     = 3000;    -- 케어 요청
SET @MISSING  = 3000;    -- 실종 제보 (care 와 같은 규모·같은 좌표 분포 → 지오 쿼리 비교가 사과 대 사과)
SET @LREVIEWS = 20000;   -- 시설 리뷰
SET @PW = '$2y$10$S7H2k5RJ1kTYDYSflTMdoeuoPtCaLBpPrXkpJgHRBlwwFTMYTH2Ni'; -- 평문: Seed1234!

-- =============================================================================
-- §1. 기존 데이터 삭제 (locationservice + MASTER 계정 제외)
-- =============================================================================
DROP TEMPORARY TABLE IF EXISTS keep_master;
CREATE TEMPORARY TABLE keep_master AS
  SELECT * FROM users WHERE email = 'wowong123@naver.com';

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE board_reaction;
TRUNCATE TABLE comment_reaction;
TRUNCATE TABLE board_view_log;
TRUNCATE TABLE comment;
TRUNCATE TABLE board;
TRUNCATE TABLE board_popularity_snapshot;
TRUNCATE TABLE missing_pet_comment;
TRUNCATE TABLE missing_pet_board;
TRUNCATE TABLE meetupparticipants;
TRUNCATE TABLE meetup;
TRUNCATE TABLE chatmessage;
TRUNCATE TABLE conversationparticipant;
TRUNCATE TABLE conversation;
TRUNCATE TABLE carereview;
TRUNCATE TABLE careapplication;
TRUNCATE TABLE carerequest_comment;
TRUNCATE TABLE carerequest;
TRUNCATE TABLE pet_coin_escrow;
TRUNCATE TABLE pet_coin_transaction;
TRUNCATE TABLE pet_vaccinations;
TRUNCATE TABLE pets;
TRUNCATE TABLE locationservicereview;
TRUNCATE TABLE notifications;
TRUNCATE TABLE fcm_token;
TRUNCATE TABLE file;
TRUNCATE TABLE report;
TRUNCATE TABLE user_sanctions;
TRUNCATE TABLE admin_audit_log;
TRUNCATE TABLE login_events;
TRUNCATE TABLE socialuser;
TRUNCATE TABLE dailystatistics;
TRUNCATE TABLE weekly_statistics;
TRUNCATE TABLE monthly_statistics;
TRUNCATE TABLE user_pet_intent_signal;
TRUNCATE TABLE signal_interaction_log;
TRUNCATE TABLE place_interaction_log;
TRUNCATE TABLE system_config;
TRUNCATE TABLE users;

INSERT INTO users SELECT * FROM keep_master;

-- 리뷰를 지웠으므로 공공데이터 시설의 평점/리뷰수도 초기화 (§6 에서 재유도)
UPDATE locationservice SET rating = 0, review_count = 0;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- §2. 숫자 헬퍼 테이블 (1 ~ 300,000)
-- =============================================================================
DROP TABLE IF EXISTS seed_numbers;
CREATE TABLE seed_numbers (n INT PRIMARY KEY);
INSERT INTO seed_numbers (n)
SELECT x FROM (
  SELECT a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000 + 1 AS x
  FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) f
) t WHERE x <= 300000;

-- =============================================================================
-- §3. 유저 · 펫
-- =============================================================================
INSERT INTO users (id, username, nickname, email, phone, password, role, location,
                   status, warning_count, pet_coin_balance, email_verified, created_at, last_login_at)
SELECT
  CONCAT('seed_user_', n),
  CONCAT('시드사용자', n),
  CONCAT('시드닉', n),
  CONCAT('seed_user_', n, '@seed.local'),
  CONCAT('010-', LPAD(n % 10000, 4, '0'), '-', LPAD((n * 7) % 10000, 4, '0')),
  @PW,
  CASE WHEN n % 1000 = 0 THEN 'ADMIN'
       WHEN n % 20 = 0   THEN 'SERVICE_PROVIDER'
       ELSE 'USER' END,
  ELT(1 + (n % 8), '서울 강남구','서울 마포구','서울 송파구','경기 성남시',
                   '경기 고양시','부산 해운대구','대구 수성구','인천 연수구'),
  'ACTIVE', 0, 0, 1,
  NOW() - INTERVAL (n % 730) DAY - INTERVAL (n % 1440) MINUTE,
  NOW() - INTERVAL (n % 60) DAY
FROM seed_numbers WHERE n <= @USERS;

SET @U0 = (SELECT MIN(idx) FROM users WHERE email LIKE 'seed_user_%');

-- 펫: 전원 1마리 + 5명 중 1명은 2마리 (반려동물 앱인데 펫 없는 유저가 태반이면 안 된다)
INSERT INTO pets (user_idx, pet_name, pet_type, breed, gender, age, weight, is_neutered, health_info, created_at)
SELECT @U0 + (n - 1),
       ELT(1 + (n % 10), '초코','보리','coco','노랑이','까미','두부','흰둥이','밤톨','뭉치','호두'),
       IF(n % 3 = 0, 'CAT', 'DOG'),
       ELT(1 + (n % 6), '말티즈','푸들','골든리트리버','코리안숏헤어','시바','비숑'),
       IF(n % 2 = 0, 'M', 'F'),   -- PetGender enum 은 M/F/UNKNOWN 이다 (MALE/FEMALE 아님)
       CONCAT(1 + (n % 15), '살'),
       ROUND(1 + (n % 30) * 0.7, 2),
       n % 2,
       IF(n % 11 = 0, '알레르기 있음 (닭고기)', NULL),
       NOW() - INTERVAL (n % 700) DAY
FROM seed_numbers WHERE n <= @USERS;

INSERT INTO pets (user_idx, pet_name, pet_type, breed, gender, age, weight, is_neutered, created_at)
SELECT @U0 + (n - 1), CONCAT('둘째', n % 100), IF(n % 2 = 0, 'CAT', 'DOG'),
       ELT(1 + (n % 4), '먼치킨','포메라니안','러시안블루','웰시코기'),
       IF(n % 2 = 0, 'F', 'M'), CONCAT(1 + (n % 10), '살'),   -- PetGender: M/F/UNKNOWN
       ROUND(1 + (n % 20) * 0.5, 2), n % 2,
       NOW() - INTERVAL (n % 500) DAY
FROM seed_numbers WHERE n <= @USERS AND n % 5 = 0;

-- =============================================================================
-- §4. 게시판 (board / comment / reaction / view_log)
-- =============================================================================
INSERT INTO board (user_idx, title, content, category, status, created_at)
SELECT @U0 + ((n * 7919) % @USERS),
       CONCAT(ELT(1 + (n % 6), '강아지 산책','고양이 사료','예방접종','펫보험','미용 후기','병원 추천'),
              ' 관련 질문드립니다 #', n),
       CONCAT('시드 게시글 본문 ', n, '. 반려동물 케어 커뮤니티 테스트용 데이터입니다. ',
              REPEAT('내용을 조금 더 채워 검색·인덱스 측정에 쓸 수 있게 합니다. ', 1 + (n % 4))),
       ELT(1 + (n % 5), 'FREE', 'QUESTION', 'INFO', 'REVIEW', 'DAILY'),
       'ACTIVE',
       NOW() - INTERVAL (n % 730) DAY - INTERVAL (n % 1440) MINUTE
FROM seed_numbers WHERE n <= @BOARDS;

SET @B0 = (SELECT MIN(idx) FROM board);

INSERT INTO comment (board_idx, user_idx, content, status, created_at)
SELECT @B0 + ((n * 104729) % @BOARDS),
       @U0 + ((n * 7919) % @USERS),
       CONCAT('시드 댓글 ', n, ' — ', ELT(1 + (n % 5),
              '도움이 되었어요.','저도 같은 경험이 있어요.','정보 감사합니다!','혹시 어느 병원인가요?','좋은 글이네요.')),
       'ACTIVE',
       NOW() - INTERVAL (n % 700) DAY - INTERVAL (n % 1440) MINUTE
FROM seed_numbers WHERE n <= @COMMENTS;

-- 리액션: 게시글마다 0~7개. 같은 게시글 안에서 user 가 겹치지 않도록 (b*13 + j) 로 분산.
-- UNIQUE(board_idx, user_idx) 를 만족한다.
INSERT IGNORE INTO board_reaction (board_idx, user_idx, reaction_type, created_at, updated_at)
SELECT @B0 + (b.n - 1),
       @U0 + ((b.n * 13 + j.n) % @USERS),
       IF((b.n + j.n) % 7 = 0, 'DISLIKE', 'LIKE'),
       NOW() - INTERVAL (b.n % 600) DAY,
       NOW() - INTERVAL (b.n % 600) DAY
FROM seed_numbers b
JOIN seed_numbers j ON j.n <= (b.n % 8)
WHERE b.n <= @BOARDS;

-- 조회 로그: 게시글마다 0~5명. view_count 는 §6 에서 이 로그를 세어서 유도한다.
INSERT IGNORE INTO board_view_log (board_id, user_id, viewed_at)
SELECT @B0 + (b.n - 1),
       @U0 + ((b.n * 31 + j.n) % @USERS),
       NOW() - INTERVAL (b.n % 400) DAY
FROM seed_numbers b
JOIN seed_numbers j ON j.n <= (b.n % 6)
WHERE b.n <= @BOARDS;

-- ── 실종 제보(missing_pet_board): 지오 검색이 걸리도록 carerequest 와 같은 좌표 분포로 채운다.
--    좌표를 carerequest 와 동일하게 두면 두 도메인의 반경 검색 비용을 나란히 비교할 수 있다.
INSERT INTO missing_pet_board (user_idx, title, content, species, breed, color, gender, age,
                               pet_name, lost_date, lost_location, latitude, longitude,
                               status, is_deleted, created_at, updated_at)
SELECT @U0 + ((n * 2287) % @USERS),
       CONCAT('강아지를 찾습니다 #', n),
       CONCAT('시드 실종 제보 ', n, '. 목격하신 분은 연락 부탁드립니다.'),
       ELT(1 + (n % 3), '개', '고양이', '기타'),
       ELT(1 + (n % 5), '푸들', '말티즈', '포메라니안', '진돗개', '믹스'),
       ELT(1 + (n % 4), '갈색', '흰색', '검정', '베이지'),
       IF(n % 2 = 0, 'M', 'F'),
       CONCAT(1 + (n % 15), '살'),
       CONCAT('보리', n),
       DATE(NOW() - INTERVAL (n % 200) DAY),
       ELT(1 + (n % 4), '서울 강남구 역삼동', '서울 마포구 연남동', '경기 성남시 분당구', '서울 송파구 잠실동'),
       37.45 + ((n % 90) * 0.004),
       126.86 + ((n % 110) * 0.004),
       CASE WHEN n % 10 < 6 THEN 'MISSING'
            WHEN n % 10 < 9 THEN 'FOUND'
            ELSE 'RESOLVED' END,
       0,
       NOW() - INTERVAL (n % 300) DAY,
       NOW() - INTERVAL (n % 300) DAY
FROM seed_numbers WHERE n <= @MISSING;

SET @MP0 = (SELECT MIN(idx) FROM missing_pet_board);

-- 목격 댓글: 제보마다 0~4건.
INSERT INTO missing_pet_comment (board_idx, user_idx, content, latitude, longitude, is_deleted, created_at, updated_at)
SELECT b.idx,
       @U0 + ((b.idx * 4099 + j.n) % @USERS),
       CONCAT('여기서 본 것 같아요! (목격 ', j.n, ')'),
       37.45 + ((b.idx % 90) * 0.004),
       126.86 + ((b.idx % 110) * 0.004),
       0,
       b.created_at + INTERVAL j.n HOUR,
       b.created_at + INTERVAL j.n HOUR
FROM missing_pet_board b
JOIN seed_numbers j ON j.n <= (b.idx % 5)
WHERE b.is_deleted = 0;

-- =============================================================================
-- §5. 모임 · 케어 · 채팅 · 코인
-- =============================================================================
-- 모임 (geo_point 는 트리거 trg_meetup_set_geo_point_insert 가 lat/lng 로 채운다)
INSERT INTO meetup (title, description, location, latitude, longitude, date,
                    organizer_idx, max_participants, status, created_at)
SELECT CONCAT(ELT(1 + (n % 5), '한강 산책','애견카페 번개','반려견 운동회','고양이 집사 모임','펫 프리마켓'),
              ' #', n),
       CONCAT('시드 모임 설명 ', n, '. 함께 즐거운 시간 보내요!'),
       ELT(1 + (n % 6), '서울숲','올림픽공원','반포한강공원','서울대공원','북서울꿈의숲','월드컵공원'),
       37.45 + ((n % 100) * 0.004),
       126.85 + ((n % 120) * 0.004),
       NOW() + INTERVAL ((n % 60) - 20) DAY,
       @U0 + ((n * 3571) % @USERS),
       5 + (n % 16),
       ELT(1 + (n % 3), 'RECRUITING', 'RECRUITING', 'CLOSED'),
       NOW() - INTERVAL (n % 300) DAY
FROM seed_numbers WHERE n <= @MEETUPS;

SET @M0 = (SELECT MIN(idx) FROM meetup);

-- 참여자: 주최자는 반드시 포함(j=0) + 추가 인원. INSERT IGNORE 로 중복 제거되어도
-- current_participants 는 §6 에서 실제 행을 세므로 항상 정합하다.
INSERT IGNORE INTO meetupparticipants (meetup_idx, user_idx, joined_at, liked)
SELECT @M0 + (m.n - 1),
       IF(j.n = 1, @U0 + ((m.n * 3571) % @USERS), @U0 + ((m.n * 17 + j.n) % @USERS)),
       NOW() - INTERVAL (m.n % 200) DAY,
       IF((m.n + j.n) % 4 = 0, 1, 0)
FROM seed_numbers m
JOIN seed_numbers j ON j.n <= LEAST(1 + (m.n % 9), 5 + (m.n % 16))
WHERE m.n <= @MEETUPS;

-- 케어 요청. status 분포: OPEN 40% / IN_PROGRESS 20% / COMPLETED 30% / CANCELLED 10%
INSERT INTO carerequest (user_idx, pet_idx, title, description, date, schedule_mode,
                         estimated_duration_minutes, status, offered_coins,
                         latitude, longitude, address, created_at, updated_at)
SELECT @U0 + ((n * 2287) % @USERS),
       (SELECT MIN(p.idx) FROM pets p WHERE p.user_idx = @U0 + ((n * 2287) % @USERS)),
       CONCAT('산책 대행 부탁드려요 #', n),
       CONCAT('시드 케어 요청 ', n, '. 잘 부탁드립니다.'),
       NOW() + INTERVAL ((n % 40) - 15) DAY,
       'FIXED',
       30 + (n % 8) * 30,
       CASE WHEN n % 10 < 4 THEN 'OPEN'
            WHEN n % 10 < 6 THEN 'IN_PROGRESS'
            WHEN n % 10 < 9 THEN 'COMPLETED'
            ELSE 'CANCELLED' END,
       1000 + (n % 10) * 500,
       37.45 + ((n % 90) * 0.004),
       126.86 + ((n % 110) * 0.004),
       ELT(1 + (n % 4), '서울 강남구 역삼동', '서울 마포구 연남동', '경기 성남시 분당구', '서울 송파구 잠실동'),
       NOW() - INTERVAL (n % 300) DAY,
       NOW() - INTERVAL (n % 300) DAY
FROM seed_numbers WHERE n <= @CARE;

SET @C0 = (SELECT MIN(idx) FROM carerequest);

-- 지원: 요청마다 1~3건. OPEN 이 아닌 요청은 첫 지원이 ACCEPTED.
INSERT INTO careapplication (care_request_idx, provider_idx, status, message, created_at)
SELECT c.idx,
       @U0 + ((c.idx * 6151 + j.n) % @USERS),
       CASE WHEN j.n = 1 AND c.status <> 'OPEN' THEN 'ACCEPTED'
            WHEN c.status = 'OPEN' THEN 'PENDING'
            ELSE 'REJECTED' END,
       CONCAT('성실히 돌봐드리겠습니다. (지원 ', j.n, ')'),
       c.created_at + INTERVAL j.n HOUR
FROM carerequest c
JOIN seed_numbers j ON j.n <= 1 + (c.idx % 3);

-- 거래 확정된 건(= OPEN 아님)에만 채팅방 생성. related 는 CARE_APPLICATION 이다.
INSERT INTO conversation (conversation_type, title, related_type, related_idx, status, created_at)
SELECT 'CARE_REQUEST', CONCAT('케어 거래 #', a.idx), 'CARE_APPLICATION', a.idx, 'ACTIVE',
       a.created_at + INTERVAL 1 HOUR
FROM careapplication a WHERE a.status = 'ACCEPTED';

-- 모임 채팅방
INSERT INTO conversation (conversation_type, title, related_type, related_idx, status, created_at)
SELECT 'MEETUP', CONCAT('모임 채팅 #', m.idx), 'MEETUP', m.idx, 'ACTIVE', m.created_at
FROM meetup m;

-- 케어 채팅방 참여자: 요청자 + 제공자. 둘 다 거래 확정(deal_confirmed=1).
INSERT INTO conversationparticipant (conversation_idx, user_idx, role, status, joined_at,
                                     deal_confirmed, deal_confirmed_at)
SELECT cv.idx, c.user_idx, 'ADMIN', 'ACTIVE', cv.created_at, 1, cv.created_at + INTERVAL 2 HOUR
FROM conversation cv
JOIN careapplication a ON a.idx = cv.related_idx AND cv.related_type = 'CARE_APPLICATION'
JOIN carerequest c ON c.idx = a.care_request_idx
WHERE cv.conversation_type = 'CARE_REQUEST';

INSERT INTO conversationparticipant (conversation_idx, user_idx, role, status, joined_at,
                                     deal_confirmed, deal_confirmed_at)
SELECT cv.idx, a.provider_idx, 'MEMBER', 'ACTIVE', cv.created_at, 1, cv.created_at + INTERVAL 2 HOUR
FROM conversation cv
JOIN careapplication a ON a.idx = cv.related_idx AND cv.related_type = 'CARE_APPLICATION'
WHERE cv.conversation_type = 'CARE_REQUEST';

-- 모임 채팅방 참여자 = 모임 참여자
INSERT IGNORE INTO conversationparticipant (conversation_idx, user_idx, role, status, joined_at)
SELECT cv.idx, mp.user_idx,
       IF(mp.user_idx = m.organizer_idx, 'ADMIN', 'MEMBER'), 'ACTIVE', mp.joined_at
FROM conversation cv
JOIN meetup m ON m.idx = cv.related_idx AND cv.related_type = 'MEETUP'
JOIN meetupparticipants mp ON mp.meetup_idx = m.idx;

-- 메시지: 대화방마다 0~9개. 보낸 사람은 그 방의 참여자 중에서 고른다.
INSERT INTO chatmessage (conversation_idx, sender_idx, message_type, content, created_at)
SELECT cv.idx,
       (SELECT cp.user_idx FROM conversationparticipant cp
        WHERE cp.conversation_idx = cv.idx
        ORDER BY cp.idx LIMIT 1 OFFSET 0),
       'TEXT',
       CONCAT('시드 메시지 ', j.n, ' — ', ELT(1 + (j.n % 5),
              '안녕하세요!','시간 괜찮으세요?','네 확인했습니다.','감사합니다 :)','그때 뵙겠습니다.')),
       cv.created_at + INTERVAL j.n * 7 MINUTE
FROM conversation cv
JOIN seed_numbers j ON j.n <= (cv.idx % 10)
WHERE EXISTS (SELECT 1 FROM conversationparticipant cp WHERE cp.conversation_idx = cv.idx);

-- ── 코인: 상태 기계대로 거래 이력을 만든다 ──────────────────────────────────
-- (1) 전 유저 충전 (CHARGE)
INSERT INTO pet_coin_transaction (user_idx, transaction_type, amount, balance_before, balance_after,
                                  related_type, related_idx, description, status, created_at)
SELECT @U0 + (n - 1), 'CHARGE', 50000, 0, 50000, NULL, NULL, '시드 초기 충전', 'COMPLETED',
       NOW() - INTERVAL 400 DAY
FROM seed_numbers WHERE n <= @USERS;

-- (2) 거래 확정 → 요청자 차감 (DEDUCT) + 에스크로 HOLD
INSERT INTO pet_coin_escrow (care_request_idx, care_application_idx, requester_idx, provider_idx,
                             amount, status, created_at, released_at, refunded_at)
SELECT c.idx, a.idx, c.user_idx, a.provider_idx, c.offered_coins,
       CASE c.status WHEN 'COMPLETED' THEN 'RELEASED'
                     WHEN 'CANCELLED' THEN 'REFUNDED'
                     ELSE 'HOLD' END,
       c.created_at + INTERVAL 3 HOUR,
       IF(c.status = 'COMPLETED', c.created_at + INTERVAL 2 DAY, NULL),
       IF(c.status = 'CANCELLED', c.created_at + INTERVAL 1 DAY, NULL)
FROM carerequest c
JOIN careapplication a ON a.care_request_idx = c.idx AND a.status = 'ACCEPTED'
WHERE c.status <> 'OPEN';

INSERT INTO pet_coin_transaction (user_idx, transaction_type, amount, balance_before, balance_after,
                                  related_type, related_idx, description, status, created_at)
SELECT e.requester_idx, 'DEDUCT', e.amount, 0, 0, 'CARE_REQUEST', e.care_request_idx,
       CONCAT('펫케어 거래 확정 - 요청 ID: ', e.care_request_idx), 'COMPLETED',
       e.created_at
FROM pet_coin_escrow e;

-- (3) 완료 → 제공자 지급 (PAYOUT)
INSERT INTO pet_coin_transaction (user_idx, transaction_type, amount, balance_before, balance_after,
                                  related_type, related_idx, description, status, created_at)
SELECT e.provider_idx, 'PAYOUT', e.amount, 0, 0, 'CARE_REQUEST', e.care_request_idx,
       CONCAT('펫케어 완료 정산 - 요청 ID: ', e.care_request_idx), 'COMPLETED',
       e.released_at
FROM pet_coin_escrow e WHERE e.status = 'RELEASED';

-- (4) 취소 → 요청자 환불 (REFUND)
INSERT INTO pet_coin_transaction (user_idx, transaction_type, amount, balance_before, balance_after,
                                  related_type, related_idx, description, status, created_at)
SELECT e.requester_idx, 'REFUND', e.amount, 0, 0, 'CARE_REQUEST', e.care_request_idx,
       CONCAT('펫케어 취소 환불 - 요청 ID: ', e.care_request_idx), 'COMPLETED',
       e.refunded_at
FROM pet_coin_escrow e WHERE e.status = 'REFUNDED';

-- 완료된 거래에 리뷰
INSERT INTO carereview (care_application_idx, reviewer_idx, reviewee_idx, rating, comment, created_at)
SELECT a.idx, c.user_idx, a.provider_idx,
       3 + (a.idx % 3),
       ELT(1 + (a.idx % 4), '친절하게 잘 돌봐주셨어요.','시간 잘 지켜주셔서 좋았습니다.',
                            '사진도 보내주시고 세심했어요.','다음에도 부탁드리고 싶어요.'),
       c.created_at + INTERVAL 3 DAY
FROM careapplication a
JOIN carerequest c ON c.idx = a.care_request_idx
WHERE a.status = 'ACCEPTED' AND c.status = 'COMPLETED';

-- 시설 리뷰 (locationservice 는 공공데이터라 그대로 두고 리뷰만 붙인다)
INSERT INTO locationservicereview (service_idx, user_idx, rating, comment, is_deleted, created_at)
SELECT ls.idx, @U0 + ((n * 4831) % @USERS),
       1 + (n % 5),
       ELT(1 + (n % 5), '시설이 깨끗해요.','주차가 편했습니다.','직원분들이 친절합니다.',
                        '가격이 합리적이에요.','재방문 의사 있어요.'),
       0,
       NOW() - INTERVAL (n % 500) DAY
FROM seed_numbers s
JOIN (SELECT idx, ROW_NUMBER() OVER (ORDER BY idx) AS rn FROM locationservice) ls
  ON ls.rn = 1 + ((s.n * 97) % (SELECT COUNT(*) FROM locationservice))
WHERE s.n <= @LREVIEWS;

-- =============================================================================
-- §6. 반정규화 카운터 유도 — 여기서만 카운터를 채운다 (전부 실제 집계에서)
-- =============================================================================
-- board.like_count / dislike_count
UPDATE board b
LEFT JOIN (SELECT board_idx, COUNT(*) c FROM board_reaction WHERE reaction_type='LIKE'    GROUP BY board_idx) l ON l.board_idx = b.idx
LEFT JOIN (SELECT board_idx, COUNT(*) c FROM board_reaction WHERE reaction_type='DISLIKE' GROUP BY board_idx) d ON d.board_idx = b.idx
SET b.like_count = IFNULL(l.c, 0), b.dislike_count = IFNULL(d.c, 0);

-- board.comment_count (미삭제 댓글만)
UPDATE board b
LEFT JOIN (SELECT board_idx, COUNT(*) c FROM comment WHERE is_deleted = 0 GROUP BY board_idx) cm ON cm.board_idx = b.idx
SET b.comment_count = IFNULL(cm.c, 0);

-- board.view_count (조회 로그 수 — 앱도 로그 삽입 성공 시에만 올린다)
UPDATE board b
LEFT JOIN (SELECT board_id, COUNT(*) c FROM board_view_log GROUP BY board_id) v ON v.board_id = b.idx
SET b.view_count = IFNULL(v.c, 0);

-- board.last_reaction_at
UPDATE board b
LEFT JOIN (SELECT board_idx, MAX(created_at) t FROM board_reaction GROUP BY board_idx) r ON r.board_idx = b.idx
SET b.last_reaction_at = r.t;

-- meetup.current_participants
UPDATE meetup m
LEFT JOIN (SELECT meetup_idx, COUNT(*) c FROM meetupparticipants GROUP BY meetup_idx) p ON p.meetup_idx = m.idx
SET m.current_participants = IFNULL(p.c, 0);

-- conversation.last_message_at / last_message_preview
UPDATE conversation cv
LEFT JOIN (SELECT conversation_idx, MAX(created_at) t FROM chatmessage GROUP BY conversation_idx) m
       ON m.conversation_idx = cv.idx
SET cv.last_message_at = m.t;

UPDATE conversation cv
JOIN chatmessage cm ON cm.conversation_idx = cv.idx AND cm.created_at = cv.last_message_at
SET cv.last_message_preview = LEFT(cm.content, 200);

-- locationservice.rating / review_count
UPDATE locationservice ls
LEFT JOIN (SELECT service_idx, AVG(rating) avg_r, COUNT(*) c
           FROM locationservicereview WHERE is_deleted = 0 GROUP BY service_idx) r ON r.service_idx = ls.idx
SET ls.rating = IFNULL(ROUND(r.avg_r, 1), 0), ls.review_count = IFNULL(r.c, 0);

-- pet_coin_transaction: balance_before / balance_after 를 유저별 러닝 밸런스로 재계산
UPDATE pet_coin_transaction t
JOIN (
  SELECT idx,
         SUM(delta) OVER (PARTITION BY user_idx ORDER BY created_at, idx
                          ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING) AS before_bal,
         SUM(delta) OVER (PARTITION BY user_idx ORDER BY created_at, idx) AS after_bal
  FROM (SELECT idx, user_idx, created_at,
               CASE transaction_type WHEN 'DEDUCT' THEN -amount ELSE amount END AS delta
        FROM pet_coin_transaction) x
) r ON r.idx = t.idx
SET t.balance_before = IFNULL(r.before_bal, 0),
    t.balance_after  = r.after_bal;

-- users.pet_coin_balance = 마지막 거래의 balance_after
UPDATE users u
LEFT JOIN (
  SELECT p.user_idx, p.balance_after
  FROM pet_coin_transaction p
  JOIN (SELECT user_idx, MAX(idx) mx FROM pet_coin_transaction GROUP BY user_idx) m
    ON m.user_idx = p.user_idx AND m.mx = p.idx
) t ON t.user_idx = u.idx
SET u.pet_coin_balance = IFNULL(t.balance_after, 0);

DROP TABLE IF EXISTS seed_numbers;
