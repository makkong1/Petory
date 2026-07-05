CREATE TABLE IF NOT EXISTS place_interaction_log (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_idx          BIGINT      NULL,
    location_idx      BIGINT      NOT NULL,
    interaction_type  VARCHAR(20) NOT NULL COMMENT 'VIEW | NAVIGATE | FAVORITE',
    created_at        DATETIME    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_place_interaction (location_idx, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='장소 행동 로그 — popularity_score 계산용';
