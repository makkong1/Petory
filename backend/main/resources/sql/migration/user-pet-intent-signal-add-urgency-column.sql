ALTER TABLE user_pet_intent_signal
    ADD COLUMN urgency VARCHAR(10) NULL COMMENT 'HIGH | NORMAL | LOW (NLP urgency_rules 결과)'
    AFTER confidence;
