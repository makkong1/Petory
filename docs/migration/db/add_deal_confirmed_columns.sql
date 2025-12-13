-- 거래 확정 기능을 위한 컬럼 추가
-- ConversationParticipant 테이블에 deal_confirmed, deal_confirmed_at 컬럼 추가

ALTER TABLE conversationparticipant
ADD COLUMN deal_confirmed BOOLEAN DEFAULT FALSE NOT NULL COMMENT '거래 확정 여부',
ADD COLUMN deal_confirmed_at DATETIME NULL COMMENT '거래 확정 시간';

-- 기존 데이터는 모두 FALSE로 설정됨 (DEFAULT 값)
-- 인덱스 추가 (선택사항, 거래 확정 상태로 조회할 경우)
-- CREATE INDEX idx_conversationparticipant_deal_confirmed ON conversationparticipant(deal_confirmed);
