-- ============================================================
-- 전체 게시글 조회 시 발생하는 SQL 쿼리 정리
-- BoardService.getAllBoards() → mapBoardsWithReactionsBatch()
-- ============================================================

-- ============================================================
-- 📋 쿼리 실행 순서 및 개요
-- ============================================================
-- 1. 게시글(Board) 조회
-- 2. 사용자(Users) 정보 조회 (JOIN 또는 별도 쿼리)
-- 3. 게시글 반응(BoardReaction) 카운트 조회 (배치)
-- 4. 첨부파일(AttachmentFile) 조회 (배치)
-- ============================================================

-- ============================================================
-- 1️⃣ 게시글(Board) 조회 쿼리
-- ============================================================
-- 메서드: BoardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc()
-- 또는: BoardRepository.findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc()
-- ============================================================

-- 전체 게시글 조회 (카테고리 없을 때)
SELECT
    b0.idx,
    b0.user_idx,
    b0.title,
    b0.content,
    b0.category,
    b0.status,
    b0.created_at,
    b0.view_count,
    b0.like_count,
    b0.comment_count,
    b0.last_reaction_at,
    b0.is_deleted,
    b0.deleted_at
FROM
    board b0
WHERE
    b0.is_deleted = false
ORDER BY
    b0.created_at DESC;

-- 카테고리별 게시글 조회
SELECT
    b0.idx,
    b0.user_idx,
    b0.title,
    b0.content,
    b0.category,
    b0.status,
    b0.created_at,
    b0.view_count,
    b0.like_count,
    b0.comment_count,
    b0.last_reaction_at,
    b0.is_deleted,
    b0.deleted_at
FROM
    board b0
WHERE
    b0.category = ?
    AND b0.is_deleted = false
ORDER BY
    b0.created_at DESC;

-- ============================================================
-- 2️⃣ 사용자(Users) 정보 조회 쿼리
-- ============================================================
-- Board 엔티티에 @ManyToOne Users가 있으므로
-- JPA가 자동으로 JOIN하거나 LAZY 로딩으로 별도 쿼리 실행
-- ============================================================

-- 만약 JOIN이 발생한다면 (EAGER 또는 명시적 JOIN)
SELECT
    b0.idx,
    b0.user_idx,
    b0.title,
    b0.content,
    b0.category,
    b0.status,
    b0.created_at,
    b0.view_count,
    b0.like_count,
    b0.comment_count,
    b0.last_reaction_at,
    b0.is_deleted,
    b0.deleted_at,
    u1.idx as user_idx,
    u1.username,
    u1.location,
    u1.email,
    u1.created_at as user_created_at
    -- ... 기타 Users 필드
FROM
    board b0
LEFT OUTER JOIN
    users u1 ON b0.user_idx = u1.idx
WHERE
    b0.is_deleted = false
ORDER BY
    b0.created_at DESC;

-- 만약 LAZY 로딩으로 별도 쿼리가 발생한다면 (N+1 문제 가능)
-- 각 게시글마다 사용자 정보를 조회
SELECT
    u0.idx,
    u0.username,
    u0.location,
    u0.email,
    u0.created_at,
    -- ... 기타 Users 필드
FROM
    users u0
WHERE
    u0.idx = ?;  -- 각 게시글의 user_idx마다 실행

-- ============================================================
-- 3️⃣ 게시글 반응(BoardReaction) 카운트 조회 쿼리
-- ============================================================
-- 메서드: BoardReactionRepository.countByBoardsGroupByReactionType()
-- 배치 크기: 500개씩 나누어 실행 (BATCH_SIZE = 500)
-- ============================================================

-- 첫 번째 배치 (boardIds 1~500)
SELECT
    br1_0.board_idx,
    br1_0.reaction_type,
    COUNT(br1_0.idx) as count
FROM
    board_reaction br1_0
WHERE
    br1_0.board_idx IN (?, ?, ?, ..., ?)  -- 최대 500개
GROUP BY
    br1_0.board_idx,
    br1_0.reaction_type;

-- 두 번째 배치 (boardIds 501~1000)
SELECT
    br1_0.board_idx,
    br1_0.reaction_type,
    COUNT(br1_0.idx) as count
FROM
    board_reaction br1_0
WHERE
    br1_0.board_idx IN (?, ?, ?, ..., ?)  -- 최대 500개
GROUP BY
    br1_0.board_idx,
    br1_0.reaction_type;

-- ... 게시글이 많으면 필요한 만큼 반복

-- 📊 결과 형태:
-- board_idx | reaction_type | count
-- ----------|---------------|------
-- 1         | LIKE          | 10
-- 1         | DISLIKE       | 2
-- 2         | LIKE          | 5
-- 2         | DISLIKE       | 0
-- ...

-- ============================================================
-- 4️⃣ 첨부파일(AttachmentFile) 조회 쿼리
-- ============================================================
-- 메서드: AttachmentFileRepository.findByTargetTypeAndTargetIdxIn()
-- ============================================================

SELECT
    a0.idx,
    a0.target_type,
    a0.target_idx,
    a0.file_path,
    a0.file_type,
    a0.created_at
FROM
    attachment_file a0
WHERE
    a0.target_type = 'BOARD'
    AND a0.target_idx IN (?, ?, ?, ..., ?)  -- 모든 boardIds (배치 제한 없음)
ORDER BY
    a0.target_idx, a0.idx;

-- ⚠️ 주의: 이 쿼리도 boardIds가 많으면 IN 절이 길어질 수 있음
-- 현재는 배치 제한이 없어서 개선 필요할 수 있음

-- ============================================================
-- 📊 전체 쿼리 실행 요약
-- ============================================================
-- 
-- 예시: 게시글이 1500개인 경우
-- 
-- 1. 게시글 조회: 1번 쿼리
-- 2. 사용자 정보: 1번 쿼리 (JOIN) 또는 1500번 쿼리 (N+1 문제)
-- 3. 반응 카운트: 3번 쿼리 (500개씩 배치)
-- 4. 첨부파일: 1번 쿼리 (모든 boardIds 포함)
-- 
-- 총 쿼리 수:
-- - 최선의 경우: 6번 (JOIN 사용 시)
-- - 최악의 경우: 1505번 (N+1 문제 발생 시)
-- ============================================================

-- ============================================================
-- 🔍 관련 테이블 구조
-- ============================================================

-- board 테이블
-- - idx (PK)
-- - user_idx (FK → users.idx)
-- - title
-- - content
-- - category
-- - status
-- - created_at
-- - view_count
-- - like_count
-- - comment_count
-- - last_reaction_at
-- - is_deleted
-- - deleted_at

-- users 테이블
-- - idx (PK)
-- - username
-- - location
-- - email
-- - created_at
-- - ... 기타 필드

-- board_reaction 테이블
-- - idx (PK)
-- - board_idx (FK → board.idx)
-- - user_idx (FK → users.idx)
-- - reaction_type (ENUM: 'LIKE', 'DISLIKE')
-- - created_at

-- attachment_file 테이블
-- - idx (PK)
-- - target_type (ENUM: 'BOARD', 'COMMENT', etc.)
-- - target_idx
-- - file_path
-- - file_type
-- - created_at

-- ============================================================
-- 💡 성능 최적화 팁
-- ============================================================
-- 
-- 1. ✅ 반응 카운트는 이미 배치 처리로 최적화됨 (500개씩)
-- 2. ⚠️ 첨부파일 조회도 배치 크기 제한 고려 필요
-- 3. ⚠️ 사용자 정보는 JOIN으로 가져오는 것이 좋음 (N+1 방지)
-- 4. 💡 페이징 처리 고려 (LIMIT, OFFSET)
-- 5. 💡 인덱스 확인:
--    - board.is_deleted, board.created_at
--    - board_reaction.board_idx, board_reaction.reaction_type
--    - attachment_file.target_type, attachment_file.target_idx
-- ============================================================

