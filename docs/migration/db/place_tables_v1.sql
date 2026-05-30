-- docs/migration/db/place_tables_v1.sql

CREATE TABLE places (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                        VARCHAR(150) NOT NULL,
    address                     VARCHAR(255),
    lat                         DOUBLE,
    lng                         DOUBLE,
    category                    VARCHAR(100),
    -- MVP: category = category3(소분류). category1/2 계층은 2단계 마이그레이션에서 추가.
    status                      ENUM('PENDING','ACTIVE','INACTIVE') NOT NULL DEFAULT 'PENDING',
    primary_source              VARCHAR(50),
    confidence                  FLOAT,
    legacy_locationservice_id   BIGINT NULL,
    activated_by                VARCHAR(100) NULL,
    activated_at                DATETIME NULL,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_places_status_confidence (status, confidence DESC),
    INDEX idx_places_legacy_ls_id      (legacy_locationservice_id)
);

CREATE TABLE place_candidates (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    raw_name                    VARCHAR(255),
    raw_address                 VARCHAR(255),
    lat                         DOUBLE,
    lng                         DOUBLE,
    collected_from              VARCHAR(100),
    evidence_text               TEXT,
    confidence_score            FLOAT,
    decision_status             ENUM('PENDING','AUTO_APPROVED','ADMIN_APPROVED','NEEDS_REVIEW','REJECTED')
                                    NOT NULL DEFAULT 'PENDING',
    decision_reason             TEXT,
    score_breakdown             JSON,
    matched_place_id            BIGINT NULL,
    matched_locationservice_id  BIGINT NULL,
    rejection_reason            VARCHAR(255) NULL,
    collected_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by                 VARCHAR(100) NULL,
    reviewed_at                 DATETIME NULL,

    INDEX idx_candidates_status_score (decision_status, confidence_score DESC),
    INDEX idx_candidates_dedup        (raw_name(100), raw_address(100)),
    INDEX idx_candidates_collected    (collected_at),

    CONSTRAINT fk_candidates_place
        FOREIGN KEY (matched_place_id) REFERENCES places (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidates_locationservice
        FOREIGN KEY (matched_locationservice_id) REFERENCES locationservice (idx) ON DELETE SET NULL
);

CREATE TABLE place_facts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id        BIGINT NOT NULL,
    fact_type       VARCHAR(100),
    value_text      TEXT,
    value_json      JSON NULL,
    source          VARCHAR(100),
    confidence      FLOAT,
    observed_at     DATE,

    INDEX idx_facts_place_type (place_id, fact_type),

    CONSTRAINT fk_facts_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE
);
