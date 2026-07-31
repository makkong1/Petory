-- carerequest FULLTEXT 검색을 ngram 파서로 전환한다.
--
-- 배경: V2 는 검색 HTTP 500(FULLTEXT 인덱스 부재) 을 막기 위한 핫픽스였고,
--       기본 InnoDB 파서로 인덱스를 붙였다. 근본 품질 개선(ngram 통일)은
--       이 마이그레이션으로 분리한다.
--
-- 왜 ngram 인가 (한글 검색):
--   1) 기본 파서는 innodb_ft_min_token_size=3 이라 '산책','병원','분양' 같은
--      2글자 한글 단어를 아예 색인하지 않는다 → 검색어로 넣어도 0건.
--   2) 기본 파서는 공백/구두점 기준 토큰화라 '강아지산책' 안의 '산책' 을
--      부분 매칭하지 못한다.
--   ngram(2글자 단위 분해) 은 이 두 갭을 메운다. board/meetup 은 처음부터
--      ngram 을 쓰고 있었고(V1 baseline), care 만 핫픽스 탓에 빠져 있었다.
--
-- 전제:
--   - ngram_token_size 는 서버 전역 설정(기본 2)이며 board/meetup 이 이미 사용 중 →
--     서버 설정 변경 불필요.
--   - carerequest FULLTEXT 인덱스는 엔티티에 매핑돼 있지 않아 ddl-auto=validate 영향 없음.

ALTER TABLE carerequest DROP INDEX idx_carerequest_title_desc;

ALTER TABLE carerequest
    ADD FULLTEXT INDEX idx_carerequest_title_desc (title, description) /*!50100 WITH PARSER ngram */;
