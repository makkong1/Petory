-- ============================================
-- SpringDataJpaBoardRepository 쿼리 EXPLAIN 테스트용
-- 워크벤치에서 실행하여 Full Scan 여부 확인
-- ============================================

-- 1. 제목으로 검색 쿼리 (LIKE %:title%)
-- 예상: Full Table Scan 발생 (인덱스 사용 불가)
EXPLAIN 
SELECT b.*, u.*
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.title LIKE '%펫케어%'
  AND b.is_deleted = false
  AND u.is_deleted = false
  AND u.status = 'ACTIVE'
ORDER BY b.created_at DESC;

-- 2. 내용으로 검색 쿼리 (LIKE %:content%)
-- 예상: Full Table Scan 발생 (인덱스 사용 불가)
EXPLAIN
SELECT b.*, u.*
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.content LIKE '%산책%'
  AND b.is_deleted = false
  AND u.is_deleted = false
  AND u.status = 'ACTIVE'
ORDER BY b.created_at DESC;

-- 3. 제목으로 검색 (페이징 포함)
-- LIMIT 추가하여 페이징 시뮬레이션
EXPLAIN
SELECT b.*, u.*
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.title LIKE '%펫케어%'
  AND b.is_deleted = false
  AND u.is_deleted = false
  AND u.status = 'ACTIVE'
ORDER BY b.created_at DESC
LIMIT 0, 20;

-- ============================================
-- 참고: LIKE 패턴별 인덱스 사용 가능 여부
-- ============================================
-- LIKE 'value%'  → 인덱스 사용 가능 (Prefix Match)
-- LIKE '%value'   → 인덱스 사용 불가 (Full Scan)
-- LIKE '%value%' → 인덱스 사용 불가 (Full Scan) ← 현재 쿼리 패턴
-- 
-- 현재 쿼리는 LIKE '%value%' 패턴이므로 Full Table Scan 발생
-- 해결 방안:
-- 1. Full-Text Search 인덱스 사용 (FULLTEXT INDEX)
-- 2. 검색 엔진 사용 (Elasticsearch 등)
-- 3. LIKE 'value%' 패턴으로 변경 (제한적)
-- ============================================

-- ============================================
-- Full-Text Search 인덱스 생성 및 확인
-- ============================================

-- 1. 현재 인덱스 확인
SHOW INDEX FROM board WHERE Key_name LIKE '%fulltext%';

-- 2. Full-Text Search 인덱스가 없으면 생성
-- (title과 content 컬럼에 대해 Full-Text 인덱스 생성)
CREATE FULLTEXT INDEX idx_fulltext_title_content 
ON board(title, content);

-- 3. 인덱스 생성 확인
SHOW INDEX FROM board WHERE Key_name = 'idx_fulltext_title_content';

-- 4. Full-Text Search를 사용한 쿼리 (인덱스 활용) - NATURAL LANGUAGE MODE
-- MATCH...AGAINST를 사용하면 인덱스를 활용할 수 있음
EXPLAIN
SELECT b.*, u.*
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE MATCH(b.title, b.content) AGAINST('펫케어' IN NATURAL LANGUAGE MODE)
  AND b.is_deleted = false
  AND u.is_deleted = false
  AND u.status = 'ACTIVE'
ORDER BY b.created_at DESC;

-- 5. Full-Text Search를 사용한 쿼리 (인덱스 활용) - BOOLEAN MODE + 관련도 점수
-- 실제 코드에서 사용하는 패턴 (searchByKeywordWithPaging)
EXPLAIN
SELECT b.*, MATCH(b.title, b.content) AGAINST('펫케어' IN BOOLEAN MODE) AS relevance
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.is_deleted = false
  AND u.is_deleted = false
  AND u.status = 'ACTIVE'
  AND MATCH(b.title, b.content) AGAINST('펫케어' IN BOOLEAN MODE)
ORDER BY relevance DESC, b.created_at DESC;

-- 6. Full-Text Search 쿼리 (페이징 포함) - 실제 사용 패턴
EXPLAIN
SELECT b.*, MATCH(b.title, b.content) AGAINST('펫케어' IN BOOLEAN MODE) AS relevance
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE b.is_deleted = false
  AND u.is_deleted = false
  AND u.status = 'ACTIVE'
  AND MATCH(b.title, b.content) AGAINST('펫케어' IN BOOLEAN MODE)
ORDER BY relevance DESC, b.created_at DESC
LIMIT 0, 20;

-- 참고: Full-Text Search 사용 시 주의사항
-- - 최소 단어 길이 제한 (기본 4자, ft_min_word_len 설정)
-- - 한글의 경우 ngram 파서 사용 권장
-- - LIKE '%value%'와 달리 자연어 검색 지원
-- ============================================

