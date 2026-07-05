-- BoardPopularitySnapshot — BaseTimeEntity.updated_at 동기화
-- 증상: Unknown column 'bps1_0.updated_at' (GET /api/boards/popular)
--
-- 적용: mysql petory < backend/main/resources/sql/migration/board-popularity-snapshot-add-updated-at-column.sql

ALTER TABLE board_popularity_snapshot
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'BaseTimeEntity (@LastModifiedDate)'
        AFTER created_at;

UPDATE board_popularity_snapshot SET updated_at = created_at;
