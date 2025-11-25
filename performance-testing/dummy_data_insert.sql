-- ============================================
-- 소규모 더미 데이터 삽입 SQL 스크립트
-- ============================================

USE petory_test;

SET FOREIGN_KEY_CHECKS = 0;
'$2a$10$0HAYjg2Zl7gzEZLJCWw.R.d1seVAOAoQ83C999OAmzihtfvjnrZu2', -- a
-- ============================================
-- Users 1000명 더미 데이터 생성
-- ============================================

SET @user_seq = 0;

INSERT INTO users (
    id, username, email, password, role, phone, location, pet_info,
    status, warning_count, created_at, updated_at
)
SELECT 
    CONCAT('dummy_user_', @user_seq := @user_seq + 1) AS id,
    CONCAT('더미사용자_', @user_seq) AS username,
    CONCAT('dummy_user_', @user_seq, '@dummy.com') AS email,
    '$2a$10$0HAYjg2Zl7gzEZLJCWw.R.d1seVAOAoQ83C999OAmzihtfvjnrZu2', AS password, -- a
    'USER' AS role,
    CONCAT('010', LPAD(@user_seq, 8, '0')) AS phone,
    ELT(1 + (@user_seq % 17),
        '서울특별시','부산광역시','대구광역시','인천광역시','광주광역시',
        '대전광역시','울산광역시','세종특별자치시','경기도 수원시','경기도 성남시',
        '경기도 고양시','강원도 춘천시','충청북도 청주시','충청남도 천안시',
        '전라북도 전주시','전라남도 목포시','경상남도 창원시'
    ) AS location,
    ELT(1 + (@user_seq % 10),
        '강아지','고양이','강아지, 고양이','햄스터','토끼',
        '고슴도치','앵무새','금붕어','거북이','기타'
    ) AS pet_info,
    'ACTIVE' AS status,
    0 AS warning_count,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*730) DAY) AS created_at,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*730) DAY) AS updated_at
FROM
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
LIMIT 1000;


-- ============================================
-- Board 5000개 더미 데이터 생성
-- ============================================

SET @board_seq = 0;
SET @max_user = (SELECT COUNT(*) FROM users); -- Users 테이블의 총 사용자 수

INSERT INTO board (
    user_idx, title, content, category, status, view_count, like_count, comment_count, is_deleted, created_at
)
SELECT
    FLOOR(1 + RAND()*@max_user) AS user_idx,
    CONCAT(
        ELT(1 + (@board_seq % 5),'건강 팁','자랑','사료 추천','병원 후기','산책 코스'),
        ' ',
        @board_seq := @board_seq + 1
    ) AS title,
    CONCAT('게시글 내용 번호: ', @board_seq, '. 오늘 강아지와 함께 공원에 갔습니다.') AS content,
    ELT(1 + (@board_seq % 5),'일상','자랑','질문','정보','후기') AS category,
    'ACTIVE' AS status,
    FLOOR(RAND()*1000) AS view_count,
    0 AS like_count,
    0 AS comment_count,
    FALSE AS is_deleted,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*365) DAY) + INTERVAL FLOOR(RAND()*24) HOUR AS created_at
FROM
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
LIMIT 5000;


-- ============================================
-- 3. Comment 테이블 (~25,000개)
-- ============================================
SET @comment_seq = 0;
SET @max_board = (SELECT MAX(idx) FROM board);

INSERT INTO comment (
    board_idx, user_idx, content, status, is_deleted, created_at
)
SELECT 
    FLOOR(1 + RAND()*@max_board) AS board_idx,
    FLOOR(1 + RAND()*@max_user) AS user_idx,
    CONCAT('댓글 번호: ', @comment_seq := @comment_seq + 1, ' 감사합니다.') AS content,
    'ACTIVE' AS status,
    FALSE AS is_deleted,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*180) DAY) + INTERVAL FLOOR(RAND()*24) HOUR
FROM
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t3
LIMIT 25000;

-- ============================================
-- 4. BoardReaction 테이블 (~15,000개)
-- ============================================
CREATE TEMPORARY TABLE IF NOT EXISTS temp_board_reactions (
    board_idx BIGINT,
    user_idx BIGINT,
    reaction_type VARCHAR(20),
    created_at DATETIME,
    PRIMARY KEY (board_idx, user_idx)
);

INSERT IGNORE INTO temp_board_reactions (
    board_idx, user_idx, reaction_type, created_at
)
SELECT 
    FLOOR(1 + RAND()*@max_board) AS board_idx,
    FLOOR(1 + RAND()*@max_user) AS user_idx,
    'LIKE' AS reaction_type,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*90) DAY)
FROM
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t3
LIMIT 15000;

INSERT IGNORE INTO board_reaction (
    board_idx, user_idx, reaction_type, created_at
)
SELECT board_idx, user_idx, reaction_type, created_at
FROM temp_board_reactions;

DROP TEMPORARY TABLE temp_board_reactions;

-- ============================================
-- 5. 통계 업데이트
-- ============================================
UPDATE board b
LEFT JOIN (
    SELECT board_idx, COUNT(*) AS cnt FROM comment WHERE is_deleted=FALSE GROUP BY board_idx
) c ON b.idx = c.board_idx
SET b.comment_count = IFNULL(c.cnt,0);

UPDATE board b
LEFT JOIN (
    SELECT board_idx, COUNT(*) AS cnt FROM board_reaction WHERE reaction_type='LIKE' GROUP BY board_idx
) br ON b.idx = br.board_idx
SET b.like_count = IFNULL(br.cnt,0);

-- ============================================
-- 6. 외래키 체크 재활성화
-- ============================================
SET FOREIGN_KEY_CHECKS = 1;
