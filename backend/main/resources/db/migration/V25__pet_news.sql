-- 서울시 보도자료의 「반려동물」 소식을 담는다.
--
-- 1) pet_news
--    게시판이 아니다. 전용 조회 화면이 없고, 사용자는 알림과 지도 배너로만 본다.
--    이 테이블이 있는 이유는 셋뿐이다 — 중복 발송 차단, 최신순 노출, 수집 이력 대조.
--
--    본문(cn)을 저장하지 않는 이유: 원문에 공공누리(KOGL) 표시가 확인되지 않았다
--    (2026-11-18 조사). 제목·부서·등록일 같은 사실 정보만 두고 본문은 원문 링크로 보낸다.
--
--    uk_pet_news_source (source, source_no)
--      멱등의 유일한 근거다. source_no 는 원문 게시물 번호(upperNo)이고,
--      재실행하면 이미 있는 행은 여기서 걸려 skipped_unchanged 로 집계된다.
--      이 제약이 없으면 매일 도는 스케줄러가 같은 소식을 다시 적재하고,
--      「신규 행에만 알림을 만든다」는 전제가 깨져 사용자에게 중복 알림이 간다.
--
--    마감일 칸이 없는 이유: 목록에 마감일 열이 없다. 접수기간은 본문에만 간헐적으로
--    있어서 행마다 상세를 1회씩 더 불러야 하는데, 그건 TourAPI 동반구역에서 이미
--    사고가 났던 구조다(일일 한도 소진 + 한 행 실패로 전량 롤백, PetTourSyncService 주석).
--    쓸 수 있는 날짜는 등록일뿐이라 published_at 만 둔다.
--
--    category 를 VARCHAR 로 두는 이유: 분류는 제목 키워드 룰이라 라벨이 늘거나 바뀐다.
--    ENUM 으로 박으면 라벨 하나 추가에 마이그레이션이 필요해진다.
--
-- 2) users.region_sigungu
--    알림 대상 판정용. 현재 소스(보도자료)는 서울시 전체 단위라 자치구로 좁힐 수 없고,
--    지금은 「값이 있으면 서울 거주자」로만 쓴다. 그런데도 시/도가 아니라 자치구로 받는 이유는,
--    원래 하려던 구청 지원사업 소스가 열리면 그대로 매칭 키가 되기 때문이다
--    (2026-11-18 조사에서 중랑구청 robots.txt 가 일반 봇을 막아 보류됐다).
--
--    NULL 을 허용하는 이유: 기존 사용자는 이 값을 입력한 적이 없다. 기본값을 넣으면
--    「서울에 산다고 밝혔다」는 거짓이 되고, 동의 없이 알림이 간다. NULL = 미설정.

CREATE TABLE `pet_news` (
    `idx`          BIGINT       NOT NULL AUTO_INCREMENT,
    `source`       VARCHAR(30)  NOT NULL COMMENT '수집 출처. 현재 SEOUL_PRESS 뿐',
    `source_no`    VARCHAR(20)  NOT NULL COMMENT '원문 게시물 번호(upperNo)',
    `title`        VARCHAR(500) NOT NULL,
    `dept`         VARCHAR(100) NULL COMMENT '담당부서. 예: 동물보호과',
    `category`     VARCHAR(20)  NOT NULL COMMENT 'EVENT / EDUCATION / SAFETY / SUPPORT',
    `published_at` DATE         NOT NULL COMMENT '원문 등록일',
    `source_url`   VARCHAR(500) NOT NULL,
    `created_at`   DATETIME     NOT NULL,
    PRIMARY KEY (`idx`),
    UNIQUE KEY `uk_pet_news_source` (`source`, `source_no`),
    KEY `idx_pet_news_published` (`published_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `users`
    ADD COLUMN `region_sigungu` VARCHAR(20) NULL COMMENT '자치구. 예: 중랑구. NULL = 미설정';
