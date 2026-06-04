-- BaseTimeEntity.updated_at 누락 테이블 일괄 보정 (나머지 도메인)
-- 이미 컬럼이 있으면 해당 ALTER 는 에러 → 그 구문만 건너뛰면 됨
--
-- 아래 3개는 별도 마이그레이션으로 처리됨:
--   file (AttachmentFile)        → file-add-updated-at-column.sql
--   board_popularity_snapshot    → board-popularity-snapshot-add-updated-at-column.sql
--   user_pet_intent_signal       → user-pet-intent-signal-add-updated-at-column.sql
--
-- 적용: mysql petory < backend/main/resources/sql/migration/remaining-domains-updated-at-catchup.sql

-- board
ALTER TABLE board
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE board SET updated_at = created_at;

-- board_reaction
ALTER TABLE board_reaction
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE board_reaction SET updated_at = created_at;

-- comment
ALTER TABLE comment
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE comment SET updated_at = created_at;

-- comment_reaction
ALTER TABLE comment_reaction
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE comment_reaction SET updated_at = created_at;

-- missing_pet_board
ALTER TABLE missing_pet_board
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE missing_pet_board SET updated_at = created_at;

-- missing_pet_comment
ALTER TABLE missing_pet_comment
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE missing_pet_comment SET updated_at = created_at;

-- care_request
ALTER TABLE care_request
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE care_request SET updated_at = created_at;

-- care_request_comment
ALTER TABLE care_request_comment
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE care_request_comment SET updated_at = created_at;

-- care_application
ALTER TABLE care_application
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE care_application SET updated_at = created_at;

-- care_review
ALTER TABLE care_review
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE care_review SET updated_at = created_at;

-- chat_message
ALTER TABLE chat_message
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE chat_message SET updated_at = created_at;

-- conversation
ALTER TABLE conversation
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE conversation SET updated_at = created_at;

-- conversation_participant
ALTER TABLE conversation_participant
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE conversation_participant SET updated_at = created_at;

-- location_service_review
ALTER TABLE location_service_review
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE location_service_review SET updated_at = created_at;

-- meetup
ALTER TABLE meetup
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE meetup SET updated_at = created_at;

-- notification
ALTER TABLE notification
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE notification SET updated_at = created_at;

-- pet_coin_escrow
ALTER TABLE pet_coin_escrow
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE pet_coin_escrow SET updated_at = created_at;

-- pet_coin_transaction
ALTER TABLE pet_coin_transaction
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE pet_coin_transaction SET updated_at = created_at;

-- place_interaction_log
ALTER TABLE place_interaction_log
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE place_interaction_log SET updated_at = created_at;

-- report
ALTER TABLE report
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE report SET updated_at = created_at;

-- daily_statistics
ALTER TABLE daily_statistics
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE daily_statistics SET updated_at = created_at;

-- monthly_statistics
ALTER TABLE monthly_statistics
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE monthly_statistics SET updated_at = created_at;

-- weekly_statistics
ALTER TABLE weekly_statistics
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE weekly_statistics SET updated_at = created_at;

-- pet
ALTER TABLE pet
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE pet SET updated_at = created_at;

-- pet_vaccination
ALTER TABLE pet_vaccination
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE pet_vaccination SET updated_at = created_at;

-- users
ALTER TABLE users
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE users SET updated_at = created_at;

-- social_user
ALTER TABLE social_user
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE social_user SET updated_at = created_at;

-- user_sanction
ALTER TABLE user_sanction
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE user_sanction SET updated_at = created_at;

-- system_config
ALTER TABLE system_config
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        AFTER created_at;
UPDATE system_config SET updated_at = created_at;
