-- meetup 테이블 FULLTEXT 인덱스 추가 (ngram 파서)
-- 목적: title, description LIKE '%keyword%' 양쪽 와일드카드 풀스캔 제거.
--       ngram_token_size=2 기준으로 2글자 단위 토큰화 → 한글 2~3글자 검색어 정상 처리.

ALTER TABLE meetup ADD FULLTEXT INDEX idx_meetup_title_description (title, description) WITH PARSER ngram;
