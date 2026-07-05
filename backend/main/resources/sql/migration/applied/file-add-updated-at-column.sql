-- AttachmentFile (@Table name = "file") — BaseTimeEntity.updated_at 동기화
-- 증상: Unknown column 'af1_0.updated_at' (게시판 목록·인기글 API 첨부파일 배치 조회 시)
--
-- 적용: mysql petory < backend/main/resources/sql/migration/file-add-updated-at-column.sql

ALTER TABLE file
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'BaseTimeEntity (@LastModifiedDate)'
        AFTER created_at;

-- 기존 행: updated_at 을 created_at 과 맞춤 (ALTER 시점 NOW() 로 채워진 값 보정)
UPDATE file SET updated_at = created_at;
