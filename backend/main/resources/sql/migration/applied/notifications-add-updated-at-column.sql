-- Notification 엔티티는 BaseTimeEntity를 상속하므로 notifications.updated_at이 필요하다.
-- 기존 remaining-domains-updated-at-catchup.sql의 단수형 테이블명(notification) 오타로
-- 해당 컬럼이 누락된 로컬 DB를 보정한다.
--
-- 적용:
-- mysql petory < backend/main/resources/sql/migration/notifications-add-updated-at-column.sql

ALTER TABLE notifications
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;

UPDATE notifications
SET updated_at = created_at;
