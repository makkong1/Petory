-- docs/migration/db/place_tables_v1.sql
-- Place Candidate Promotion System (1단계 MVP)
-- 수집 → 후보(place_candidates) → 4-gate 판정 → 확정 장소(places) → 서비스 노출(ACTIVE)
-- place_facts는 2단계 이후 자동 수집 예정 (1단계: 테이블 생성만)

CREATE TABLE places (
    -- 서비스 canonical 장소. AUTO_APPROVED/ADMIN_APPROVED 후보에서 승격된 확정 장소.
    -- status=ACTIVE인 레코드만 서비스 API에 노출. PENDING=승격됐지만 미노출, INACTIVE=비활성.
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
) COMMENT = '서비스 canonical 장소. place_candidates 승격 결과. status=ACTIVE만 서비스 노출.';

CREATE TABLE place_candidates (
    -- 수집 파이프라인(pet-data-api)에서 들어온 미확정 장소 후보.
    -- 4-gate 판정 엔진이 PENDING → AUTO_APPROVED/NEEDS_REVIEW/REJECTED 로 분류.
    -- 직접 서비스 API 노출 금지. 관리자 검수 후 places로 승격.
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    raw_name                    VARCHAR(255) NOT NULL,
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
    INDEX idx_candidates_dedup        (raw_name, raw_address),
    INDEX idx_candidates_collected    (collected_at),
    INDEX idx_candidates_matched_place (matched_place_id),

    CONSTRAINT fk_candidates_place
        FOREIGN KEY (matched_place_id) REFERENCES places (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidates_locationservice
        FOREIGN KEY (matched_locationservice_id) REFERENCES locationservice (idx) ON DELETE SET NULL
) COMMENT = '미확정 장소 후보. pet-data-api 수집 결과 적재. 4-gate 판정 후 places 승격 또는 탈락.';

CREATE TABLE place_facts (
    -- 장소별 개별 사실(영업시간·전화·반려동물 정책 등)을 fact_type+source+confidence 구조로 저장.
    -- 동일 fact_type에 여러 source가 존재할 수 있으며, confidence 높은 값을 우선 사용.
    -- 2단계 이후 공공데이터 strong match 시 자동 적재 예정.
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
) COMMENT = '장소별 사실 저장. fact_type(OPERATING_HOURS·PHONE 등)+source+confidence 구조. 2단계 이후 자동 수집.';
