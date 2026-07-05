CREATE TABLE IF NOT EXISTS signal_interaction_log (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_idx         BIGINT       NOT NULL,
    signal_id        BIGINT       NOT NULL COMMENT 'user_pet_intent_signal.id',
    intent_domain    VARCHAR(50)  NOT NULL,
    target_tab       VARCHAR(30)  NULL     COMMENT 'location | care | meetup | missingPet',
    target_category  VARCHAR(100) NULL,
    interaction_type VARCHAR(20)  NOT NULL COMMENT 'CLICKED | DISMISSED | CONVERTED',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_signal_log_user    (user_idx, created_at),
    INDEX idx_signal_log_signal  (signal_id),
    INDEX idx_signal_log_domain  (intent_domain, interaction_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='추천 카드 상호작용 로그 — threshold 튜닝 및 카드 문구 개선 근거';
