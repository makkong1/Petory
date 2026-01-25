-- 펫코인 시스템을 위한 테이블 생성
-- 1. Users 테이블에 펫코인 잔액 컬럼 추가
-- 2. 펫코인 거래 내역 테이블 생성

-- ============================================
-- 1. Users 테이블에 펫코인 잔액 컬럼 추가
-- ============================================
ALTER TABLE users
ADD COLUMN pet_coin_balance INT DEFAULT 0 NOT NULL COMMENT '펫코인 잔액' AFTER warning_count;

-- 기존 사용자는 모두 0 코인으로 시작

-- ============================================
-- 2. 펫코인 거래 내역 테이블 생성
-- ============================================
CREATE TABLE pet_coin_transaction (
    idx BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '거래 내역 ID',
    user_idx BIGINT NOT NULL COMMENT '사용자 ID',
    transaction_type VARCHAR(20) NOT NULL COMMENT '거래 타입 (CHARGE: 충전, DEDUCT: 차감, PAYOUT: 지급, REFUND: 환불)',
    amount INT NOT NULL COMMENT '거래 금액 (코인 단위)',
    balance_before INT NOT NULL COMMENT '거래 전 잔액',
    balance_after INT NOT NULL COMMENT '거래 후 잔액',
    related_type VARCHAR(50) NULL COMMENT '관련 엔티티 타입 (CARE_REQUEST 등)',
    related_idx BIGINT NULL COMMENT '관련 엔티티 ID',
    description VARCHAR(500) NULL COMMENT '거래 설명',
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT '거래 상태 (PENDING, COMPLETED, FAILED, CANCELLED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    
    INDEX idx_user_idx (user_idx),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_related (related_type, related_idx),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status),
    
    FOREIGN KEY (user_idx) REFERENCES users(idx) ON DELETE CASCADE,
    
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_balance_after CHECK (balance_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='펫코인 거래 내역';

-- ============================================
-- 3. 펫코인 에스크로 테이블 생성 (거래 확정 시 임시 보관)
-- ============================================
CREATE TABLE pet_coin_escrow (
    idx BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '에스크로 ID',
    care_request_idx BIGINT NOT NULL COMMENT '펫케어 요청 ID',
    care_application_idx BIGINT NULL COMMENT '펫케어 지원 ID (거래 확정 시 생성)',
    requester_idx BIGINT NOT NULL COMMENT '요청자 ID',
    provider_idx BIGINT NOT NULL COMMENT '제공자 ID',
    amount INT NOT NULL COMMENT '에스크로 금액 (코인 단위)',
    status VARCHAR(20) NOT NULL DEFAULT 'HOLD' COMMENT '에스크로 상태 (HOLD: 보관중, RELEASED: 지급완료, REFUNDED: 환불완료)',
    released_at DATETIME NULL COMMENT '지급 시간',
    refunded_at DATETIME NULL COMMENT '환불 시간',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    
    UNIQUE KEY uk_care_request (care_request_idx),
    INDEX idx_requester (requester_idx),
    INDEX idx_provider (provider_idx),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    
    FOREIGN KEY (care_request_idx) REFERENCES carerequest(idx) ON DELETE CASCADE,
    FOREIGN KEY (care_application_idx) REFERENCES careapplication(idx) ON DELETE SET NULL,
    FOREIGN KEY (requester_idx) REFERENCES users(idx) ON DELETE CASCADE,
    FOREIGN KEY (provider_idx) REFERENCES users(idx) ON DELETE CASCADE,
    
    CONSTRAINT chk_escrow_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='펫코인 에스크로 (거래 확정 시 임시 보관)';
