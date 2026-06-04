-- BaseTimeEntity.updated_at 누락 테이블 일괄 보정 (로컬 DB 스키마가 엔티티보다 오래된 경우)
-- 이미 컬럼이 있으면 해당 ALTER 는 에러 → 그 구문만 건너뛰면 됨
--
-- 적용: mysql petory < backend/main/resources/sql/migration/base-time-entity-updated-at-catchup.sql

-- file (AttachmentFile) — af1_0.updated_at
ALTER TABLE file
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE file SET updated_at = created_at;

-- board_popularity_snapshot — bps1_0.updated_at
ALTER TABLE board_popularity_snapshot
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE board_popularity_snapshot SET updated_at = created_at;

-- user_pet_intent_signal — signal INSERT 시 updated_at
ALTER TABLE user_pet_intent_signal
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE user_pet_intent_signal SET updated_at = created_at;
