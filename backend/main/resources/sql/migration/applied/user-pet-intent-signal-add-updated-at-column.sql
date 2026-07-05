-- UserPetIntentSignal — BaseTimeEntity.updated_at 동기화
-- 증상: insert user_pet_intent_signal 시 Unknown column 'updated_at'
-- (게시글 저장은 성공, 비동기 signal 저장만 실패)
--
-- 적용: mysql petory < backend/main/resources/sql/migration/user-pet-intent-signal-add-updated-at-column.sql

ALTER TABLE user_pet_intent_signal
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'BaseTimeEntity (@LastModifiedDate)'
        AFTER created_at;

UPDATE user_pet_intent_signal SET updated_at = created_at;
