CREATE TABLE IF NOT EXISTS user_pet_intent_signal (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    user_idx                BIGINT       NOT NULL,
    source_type             VARCHAR(30)  NOT NULL COMMENT 'COMMUNITY | CARE | LOCATION_SEARCH',
    source_id               BIGINT       NULL,
    intent_domain           VARCHAR(50)  NOT NULL,
    intent                  VARCHAR(50)  NOT NULL,
    recommended_categories  JSON         NULL,
    confidence              DOUBLE       NOT NULL,
    intent_tags             JSON         NULL,
    created_at              DATETIME     NOT NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at              DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_user_signal_active (user_idx, expires_at, created_at),
    INDEX idx_signal_source      (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 반려생활 의도 signal (원문 저장 없음, TTL 7일)';
