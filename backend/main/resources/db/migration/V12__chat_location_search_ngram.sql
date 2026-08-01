-- chatmessage · locationservice 의 FULLTEXT 검색을 ngram 파서로 전환한다.
-- 이로써 FULLTEXT 인덱스 5개(board·meetup·carerequest·chatmessage·locationservice)가 전부 ngram 이 된다.
--
-- 배경: 왜 이제서야 바꾸나
--   V9(carerequest) 때는 이 두 개를 의도적으로 남겼다. 근거는 두 가지였다.
--     (1) 프론트에 호출부가 없다 — 채팅 검색은 API 만 있고 부르는 컴포넌트가 없고,
--         장소 검색은 지도가 항상 좌표를 실어 보내 반경 검색으로 빠진다.
--     (2) performance_schema 로 봐도 ft_search 의 COUNT_READ 가 0 이다.
--
--   2026-08-01 재점검에서 (2)가 무효라는 걸 확인했다.
--   performance_schema.table_io_waits_summary_by_index_usage 는 InnoDB FULLTEXT
--   보조 테이블 접근을 집계하지 않는다. 실제로 ft_search 를 타는 쿼리를 날려
--   13,003 행을 받은 직후에도 COUNT_READ 는 그대로 0 이었다(chatmessage 도 동일).
--   즉 안 써도 0, 써도 0 이라 "안 쓰인다"의 근거가 될 수 없다.
--
--   (1)은 사실이지만 "우리 프론트가 안 부른다"이지 "그 코드가 안 돈다"가 아니다.
--   두 엔드포인트 모두 살아 있는 인증 API 다.
--     · ChatMessageController      GET /api/messages/conversation/{idx}/search
--     · LocationServiceController  GET /api/location-services/search?keyword=  (분기 ③)
--
-- 무엇이 깨져 있었나 (2026-08-01, 로컬 petory, 커밋 ccda0c72)
--   기본 파서는 innodb_ft_min_token_size=3 이라 2글자 한글을 색인하지 않는다.
--
--   chatmessage — 아예 0 건이 된다.
--     '시드'(2글자)  LIKE 30,600 건  /  FULLTEXT 0 건
--     '메시지'(3글자) LIKE 30,600 건  /  FULLTEXT 30,600 건
--
--   locationservice — 0 건이 아니라 "조용히 일부만" 나온다. 이쪽이 더 나쁘다.
--     쿼리가 AGAINST(CONCAT(:keyword,'*') IN BOOLEAN MODE) 라, 2글자 키워드가
--     3글자 이상 토큰의 접두사로 걸리는 것만 살아남는다.
--     '카페'  name LIKE 272 건 → FULLTEXT 55 건 (80% 유실)
--     '미용'  name LIKE 352 건 → FULLTEXT 19 건 (95% 유실)
--     0 건이면 바로 이상하다고 알아채지만 55 건은 정상처럼 보인다.
--     carerequest 가 HTTP 500 으로 즉시 발견됐던 것과 정반대 실패 유형이다.
--
-- 전제 / 확인한 것
--   - ngram_token_size 는 서버 전역 설정(현재 2)이며 board/meetup/carerequest 가
--     이미 쓰고 있다 → 서버 설정 변경 불필요.
--   - 접두사 와일드카드 호환: 이미 ngram 인 board 에서 AGAINST('산책*' IN BOOLEAN MODE)
--     = AGAINST('산책') = LIKE '%산책%' 로 8,333 건 동일. locationservice 쿼리의 '*' 를
--     고칠 필요가 없다.
--   - 1글자 키워드는 ngram 에서 0 건인데 기본 파서도 0 건이라 회귀가 아니다.
--   - 두 FULLTEXT 인덱스 모두 엔티티에 매핑돼 있지 않아 ddl-auto=validate 영향 없음.
--   - 재생성 비용: chatmessage 30,600 행 / locationservice 24,130 행.
--
-- 회귀 가드: backend/test/.../global/performance/FulltextParserRegressionTest.java

ALTER TABLE chatmessage DROP INDEX idx_chat_message_content;

ALTER TABLE chatmessage
    ADD FULLTEXT INDEX idx_chat_message_content (content) /*!50100 WITH PARSER ngram */;

ALTER TABLE locationservice DROP INDEX ft_search;

ALTER TABLE locationservice
    ADD FULLTEXT INDEX ft_search (name, description, category1, category2, category3) /*!50100 WITH PARSER ngram */;
