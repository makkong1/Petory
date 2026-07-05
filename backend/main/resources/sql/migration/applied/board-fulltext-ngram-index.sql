-- board 테이블 FULLTEXT 인덱스를 ngram 파서로 교체
-- 목적: 기존 standard 파서(ft_min_word_len=4)는 2~3글자 한글 검색어를 인덱싱하지 않아
--       "강아지"(3글자), "산책"(2글자) 등 주요 검색어가 결과 없음으로 나타나는 문제 수정.
--       ngram 파서(ngram_token_size=2)는 텍스트를 2글자 단위로 분리해 저장하므로
--       짧은 한글 단어도 정상 검색된다.

ALTER TABLE board DROP INDEX idx_board_title_content;
ALTER TABLE board ADD FULLTEXT INDEX idx_board_title_content (title, content) WITH PARSER ngram;
