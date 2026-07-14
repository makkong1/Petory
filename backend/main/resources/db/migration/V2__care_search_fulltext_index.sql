-- carerequest 검색이 HTTP 500 이던 것을 고친다.
--
-- 증상: GET /api/care-requests/search 와 GET /api/admin/care-requests?q= 가 항상 500.
--       java.sql.SQLException: Can't find FULLTEXT index matching the column list
--
-- 원인: 두 쿼리 모두 MATCH(cr.title, cr.description) AGAINST(...) 를 쓰는데
--       carerequest 에 FULLTEXT 인덱스가 없다. MySQL 은 FULLTEXT 인덱스 없이는
--       MATCH...AGAINST 를 실행 자체를 못 한다 (느린 게 아니라 에러다).
--
-- board 는 같은 형태의 인덱스(idx_board_title_content)를 이미 갖고 있다. care 만 빠져 있었다.
-- 근거: docs/analysis/query-audit/care-2026-07-14.md §2

ALTER TABLE carerequest
    ADD FULLTEXT INDEX idx_carerequest_title_desc (title, description);
