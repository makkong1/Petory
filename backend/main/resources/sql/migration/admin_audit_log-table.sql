CREATE TABLE IF NOT EXISTS admin_audit_log (
    idx BIGINT NOT NULL AUTO_INCREMENT,
    admin_idx BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NULL,
    target_idx BIGINT NULL,
    detail VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (idx),
    INDEX idx_audit_admin_created (admin_idx, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
