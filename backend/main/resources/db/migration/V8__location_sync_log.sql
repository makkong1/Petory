-- 공공데이터 위치 동기화 파이프라인 실행 이력
CREATE TABLE location_sync_log (
    idx           BIGINT       NOT NULL AUTO_INCREMENT,
    started_at    DATETIME     NOT NULL,
    finished_at   DATETIME     NULL,
    status        VARCHAR(20)  NOT NULL,
    total_fetched INT          NOT NULL DEFAULT 0,
    inserted      INT          NOT NULL DEFAULT 0,
    updated       INT          NOT NULL DEFAULT 0,
    skipped       INT          NOT NULL DEFAULT 0,
    failed        INT          NOT NULL DEFAULT 0,
    trigger_type  VARCHAR(20)  NOT NULL,
    error_message TEXT         NULL,
    PRIMARY KEY (idx),
    KEY idx_location_sync_log_started (started_at DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
