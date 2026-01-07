-- ============================================
-- 대용량 더미 데이터 삽입 SQL 스크립트 (개선 버전)
-- ============================================
-- 목표: Users 10,000명, Board 100,000개, Comment 1,000,000개, BoardReaction 500,000개
-- 
-- 주의사항:
-- 1. 실행 전 백업 필수
-- 2. 테스트 DB에서만 실행
-- 3. 외래키 체크 해제 시 성능 향상 (주의 필요)
-- 4. 배치 단위로 나눠서 실행 권장 (메모리 부족 방지)

-- ============================================
-- 설정
-- ============================================
SET @start_time = NOW();
SET FOREIGN_KEY_CHECKS = 0;  -- 성능 향상을 위해 임시 해제 (주의!)
SET UNIQUE_CHECKS = 0;         -- UNIQUE 체크 임시 해제 (주의!)
SET AUTOCOMMIT = 0;            -- 트랜잭션으로 묶어서 성능 향상

-- ============================================
-- 1. Users 테이블 더미 데이터 생성 (10,000명)
-- ============================================
-- 배치 단위: 1000개씩 10번 실행

-- 첫 번째 배치 (1000개)
INSERT INTO users (
    id, username, email, password, role, phone, location, petInfo, 
    status, warning_count, created_at, updated_at
)
SELECT 
    CONCAT('dummy_user', seq) AS id,
    CONCAT('더미사용자', seq) AS username,
    CONCAT('dummy', seq, '@test.com') AS email,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' AS password,
    'USER' AS role,
    CONCAT('010', LPAD(seq, 8, '0')) AS phone,
    ELT(1 + (seq % 5), '서울시 강남구', '서울시 서초구', '서울시 송파구', '서울시 마포구', '서울시 종로구') AS location,
    ELT(1 + (seq % 4), '강아지', '고양이', '강아지, 고양이', '햄스터') AS petInfo,
    'ACTIVE' AS status,
    0 AS warning_count,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 730) DAY) AS created_at,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 730) DAY) AS updated_at
FROM (
    SELECT @row := @row + 1 AS seq
    FROM (SELECT @row := (SELECT COALESCE(MAX(idx), 0) FROM users)) AS r
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t3
    LIMIT 1000
) AS numbers;

COMMIT;

-- 나머지 9개 배치도 동일한 방식으로 실행 (총 10,000개)
-- (실제로는 반복문이나 스크립트로 처리)

-- ============================================
-- 2. Board 테이블 더미 데이터 생성 (100,000개)
-- ============================================
-- Users의 idx 범위 확인 필요
-- SELECT MIN(idx) AS min_user_idx, MAX(idx) AS max_user_idx FROM users;

-- 배치 단위: 10,000개씩 10번 실행
INSERT INTO board (
    user_idx, title, content, category, status, 
    view_count, like_count, comment_count, 
    is_deleted, created_at
)
SELECT 
    (SELECT idx FROM users ORDER BY RAND() LIMIT 1) AS user_idx,
    CONCAT(
        ELT(1 + (seq % 5), 
            '반려동물 건강 관리 팁', 
            '우리 강아지 자랑', 
            '고양이 사료 추천', 
            '동물병원 후기',
            '산책 코스 추천'
        ),
        ' ',
        seq
    ) AS title,
    CONCAT(
        '게시글 내용입니다. 번호: ', seq, '\n\n',
        '오늘 우리 강아지와 함께 공원에 갔어요. 날씨도 좋고 강아지도 너무 좋아했어요. ',
        '최근에 새로운 사료를 바꿨는데 반려동물이 잘 먹고 있어서 추천드려요.'
    ) AS content,
    ELT(1 + (seq % 5), '자유게시판', '질문게시판', '정보공유', '후기', '공지사항') AS category,
    'ACTIVE' AS status,
    FLOOR(RAND() * 10000) AS view_count,
    0 AS like_count,
    0 AS comment_count,
    FALSE AS is_deleted,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY) + 
    INTERVAL FLOOR(RAND() * 24) HOUR + 
    INTERVAL FLOOR(RAND() * 60) MINUTE AS created_at
FROM (
    SELECT @board_row := @board_row + 1 AS seq
    FROM (SELECT @board_row := (SELECT COALESCE(MAX(idx), 0) FROM board)) AS r
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t3
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t4
    LIMIT 10000
) AS numbers;

COMMIT;

-- ============================================
-- 3. Comment 테이블 더미 데이터 생성 (1,000,000개)
-- ============================================
-- 배치 단위: 50,000개씩 20번 실행 (메모리 부족 방지)

INSERT INTO comment (
    board_idx, user_idx, content, status, is_deleted, created_at
)
SELECT 
    (SELECT idx FROM board ORDER BY RAND() LIMIT 1) AS board_idx,
    (SELECT idx FROM users ORDER BY RAND() LIMIT 1) AS user_idx,
    CONCAT('댓글 내용입니다. 번호: ', seq, '\n\n', '좋은 정보 감사합니다!') AS content,
    'ACTIVE' AS status,
    FALSE AS is_deleted,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 180) DAY) + 
    INTERVAL FLOOR(RAND() * 24) HOUR + 
    INTERVAL FLOOR(RAND() * 60) MINUTE AS created_at
FROM (
    SELECT @comment_row := @comment_row + 1 AS seq
    FROM (SELECT @comment_row := (SELECT COALESCE(MAX(idx), 0) FROM comment)) AS r
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t3
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t4
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t5
    LIMIT 50000
) AS numbers;

COMMIT;

-- ============================================
-- 4. BoardReaction 테이블 더미 데이터 생성 (500,000개)
-- ============================================
-- UNIQUE 제약조건 때문에 중복 체크 필요
-- 배치 단위: 25,000개씩 20번 실행

-- 임시 테이블로 중복 방지 (더 빠름)
CREATE TEMPORARY TABLE IF NOT EXISTS temp_reactions (
    board_idx BIGINT,
    user_idx BIGINT,
    reaction_type VARCHAR(20),
    created_at DATETIME,
    PRIMARY KEY (board_idx, user_idx)
);

-- 임시 테이블에 데이터 생성
INSERT INTO temp_reactions (board_idx, user_idx, reaction_type, created_at)
SELECT 
    (SELECT idx FROM board ORDER BY RAND() LIMIT 1) AS board_idx,
    (SELECT idx FROM users ORDER BY RAND() LIMIT 1) AS user_idx,
    'LIKE' AS reaction_type,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 90) DAY) + 
    INTERVAL FLOOR(RAND() * 24) HOUR + 
    INTERVAL FLOOR(RAND() * 60) MINUTE AS created_at
FROM (
    SELECT @reaction_row := @reaction_row + 1 AS seq
    FROM (SELECT @reaction_row := 0) AS r
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t3
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t4
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION 
                SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t5
    LIMIT 25000
) AS numbers
ON DUPLICATE KEY UPDATE board_idx = board_idx;  -- 중복 무시

-- 실제 테이블에 삽입
INSERT INTO board_reaction (board_idx, user_idx, reaction_type, created_at)
SELECT board_idx, user_idx, reaction_type, created_at
FROM temp_reactions
ON DUPLICATE KEY UPDATE board_idx = board_idx;  -- 중복 무시

DROP TEMPORARY TABLE temp_reactions;
COMMIT;

-- ============================================
-- 5. 통계 업데이트 (Board 테이블의 카운트 필드)
-- ============================================

-- 댓글 수 업데이트 (배치로 나눠서 실행)
UPDATE board b
INNER JOIN (
    SELECT board_idx, COUNT(*) AS cnt
    FROM comment
    WHERE is_deleted = FALSE
    GROUP BY board_idx
) AS c ON b.idx = c.board_idx
SET b.comment_count = c.cnt;

COMMIT;

-- 좋아요 수 업데이트 (배치로 나눠서 실행)
UPDATE board b
INNER JOIN (
    SELECT board_idx, COUNT(*) AS cnt
    FROM board_reaction
    WHERE reaction_type = 'LIKE'
    GROUP BY board_idx
) AS br ON b.idx = br.board_idx
SET b.like_count = br.cnt;

COMMIT;

-- ============================================
-- 설정 복원 및 완료
-- ============================================
SET FOREIGN_KEY_CHECKS = 1;
SET UNIQUE_CHECKS = 1;
SET AUTOCOMMIT = 1;

SELECT 
    @start_time AS start_time,
    NOW() AS end_time,
    TIMESTAMPDIFF(SECOND, @start_time, NOW()) AS elapsed_seconds;

-- ============================================
-- 데이터 확인 쿼리
-- ============================================
-- SELECT COUNT(*) AS user_count FROM users;
-- SELECT COUNT(*) AS board_count FROM board;
-- SELECT COUNT(*) AS comment_count FROM comment;
-- SELECT COUNT(*) AS reaction_count FROM board_reaction;

