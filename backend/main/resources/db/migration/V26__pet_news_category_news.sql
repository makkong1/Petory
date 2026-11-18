-- pet_news.category 에 NEWS 를 더한다. 컬럼 주석만 바꾸는 마이그레이션이다.
--
-- V25 를 쓸 때는 분류가 EVENT/EDUCATION/SAFETY/SUPPORT 넷이면 충분할 줄 알았고,
-- 어디에도 안 걸리는 제목은 버릴 생각이었다. 실제로 긁어보니(2026-11-18, 1~2페이지 20건)
-- 버려지는 것 중에 「서울동물행복위크 시작」, 「반려동물을 보낸 슬픔, 서울시와 함께
-- 치유하세요」처럼 **사용자에게 보여야 할 소식**이 들어 있었다. 분류가 안 될 뿐 잡음이 아니다.
--
-- 잡음은 다른 곳에서 걸러진다 — 검색(srchText)이 본문까지 훑어 반려동물과 무관한 보도자료가
-- 섞여 들어오는데(20건 중 5건), 크롤러가 **제목에 반려동물 신호어가 있는지**로 먼저 거른다.
-- 그 관문을 통과했는데 분류만 안 되는 경우가 NEWS 다.
--
-- category 를 ENUM 이 아니라 VARCHAR 로 둔 것이 여기서 값을 했다. 데이터·제약 변경은 없고
-- 주석만 고친다. 주석을 그냥 두면 스키마가 조용히 거짓이 된다.

ALTER TABLE `pet_news`
    MODIFY COLUMN `category` VARCHAR(20) NOT NULL
    COMMENT 'EVENT / EDUCATION / SAFETY / SUPPORT / NEWS(분류 안 된 반려동물 소식)';
