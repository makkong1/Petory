-- board FULLTEXT 검색 쿼리 직접 실행 + EXPLAIN
-- 대상: SpringDataJpaBoardRepository.searchByKeywordWithPaging (native query)
--
-- 실행: mysql -u root -p petory < scripts/explain/board-fulltext-search.sql
-- 또는 mysql 접속 후: source scripts/explain/board-fulltext-search.sql
--
-- 검색어를 바꾸려면 아래 @kw 한 줄만 수정.

SET @kw = '강아지';

-- ─────────────────────────────────────────────
-- 0. 전제 확인: FULLTEXT 인덱스가 실제로 있는지
--    idx_board_title_content (title, content) WITH PARSER ngram 이 보여야 한다
-- ─────────────────────────────────────────────
SELECT '=== 0. board 테이블의 FULLTEXT 인덱스 ===' AS section;
SHOW INDEX FROM board WHERE Index_type = 'FULLTEXT';

-- ─────────────────────────────────────────────
-- 1. 본 쿼리 실행 결과 (앱이 실제로 던지는 SQL과 동일, LIMIT은 1페이지 10건 가정)
--    relevance 컬럼 = MATCH가 계산한 관련도 점수. 이 값 내림차순으로 정렬됨을 눈으로 확인
-- ─────────────────────────────────────────────
SELECT '=== 1. 검색 결과 (relevance 점수 포함, 상위 10건) ===' AS section;
SELECT b.idx,
       LEFT(b.title, 30)  AS title,
       MATCH(b.title, b.content) AGAINST(@kw IN BOOLEAN MODE) AS relevance,
       b.created_at
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.is_deleted = false
  AND u.is_deleted = false
  AND b.author_visible = 1
  AND MATCH(b.title, b.content) AGAINST(@kw IN BOOLEAN MODE)
ORDER BY relevance DESC, b.created_at DESC
LIMIT 10;

-- ─────────────────────────────────────────────
-- 2. countQuery 실행 결과 (Page의 totalElements가 이 값)
-- ─────────────────────────────────────────────
SELECT '=== 2. COUNT (totalElements) ===' AS section;
SELECT COUNT(*) AS total
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.is_deleted = false
  AND u.is_deleted = false
  AND b.author_visible = 1
  AND MATCH(b.title, b.content) AGAINST(@kw IN BOOLEAN MODE);

-- ─────────────────────────────────────────────
-- 3. EXPLAIN — 실행 계획
--    확인 포인트:
--    - board 행: type=fulltext, key=idx_board_title_content, Extra에 "Using where; Ft_hints: sorted"류
--      → FULLTEXT 인덱스로 후보를 좁힌 뒤 나머지 조건(is_deleted 등)을 필터한다는 뜻
--    - users 행: type=eq_ref, key=PRIMARY → PK로 작성자 1건씩 조인
--    - 만약 board 행이 type=ALL이면 FULLTEXT 인덱스를 안 탄 것 (인덱스 누락/검색어 문제)
-- ─────────────────────────────────────────────
SELECT '=== 3. EXPLAIN ===' AS section;
EXPLAIN
SELECT b.*, MATCH(b.title, b.content) AGAINST(@kw IN BOOLEAN MODE) AS relevance
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.is_deleted = false
  AND u.is_deleted = false
  AND b.author_visible = 1
  AND MATCH(b.title, b.content) AGAINST(@kw IN BOOLEAN MODE)
ORDER BY relevance DESC, b.created_at DESC;

-- ─────────────────────────────────────────────
-- 4. EXPLAIN ANALYZE — 실제 실행해서 단계별 소요 시간·행 수까지 측정
--    (MySQL 8.0.18+. 진짜로 쿼리를 실행하므로 시간이 그대로 나온다)
--    읽는 법: 안쪽(들여쓰기 깊은 쪽)부터 실행. actual time=시작..끝(ms), rows=실제 처리 행 수
-- ─────────────────────────────────────────────
SELECT '=== 4. EXPLAIN ANALYZE ===' AS section;
EXPLAIN ANALYZE
SELECT b.*, MATCH(b.title, b.content) AGAINST(@kw IN BOOLEAN MODE) AS relevance
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.is_deleted = false
  AND u.is_deleted = false
  AND b.author_visible = 1
  AND MATCH(b.title, b.content) AGAINST(@kw IN BOOLEAN MODE)
ORDER BY relevance DESC, b.created_at DESC;

-- ─────────────────────────────────────────────
-- 5. 비교용: 같은 검색을 LIKE '%...%'로 하면 어떻게 되나
--    실측(2026-07-18, board 48,620행): type=ref, key=idx_board_deleted_created, rows=24310, filtered=2.10
--    → is_deleted 인덱스로 절반을 잡은 뒤 그 24,310행 전부에 LIKE를 돌려 2.1%만 남긴다.
--      LIKE 자체는 인덱스를 전혀 못 쓰고(possible_keys에 FULLTEXT 없음) 행마다 문자열 스캔.
--    3번 FULLTEXT는 인덱스에서 매칭 8,333행을 바로 꺼냈다 — 읽는 행 수 차이가 핵심.
-- ─────────────────────────────────────────────
SELECT '=== 5. 비교: LIKE 풀스캔 버전 EXPLAIN ===' AS section;
EXPLAIN
SELECT b.*
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.is_deleted = false
  AND u.is_deleted = false
  AND b.author_visible = 1
  AND (b.title LIKE CONCAT('%', @kw, '%') OR b.content LIKE CONCAT('%', @kw, '%'))
ORDER BY b.created_at DESC;
