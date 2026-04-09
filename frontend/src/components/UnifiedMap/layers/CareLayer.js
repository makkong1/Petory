import React, { useState } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../../contexts/AuthContext';
import { getOrCreateDirectConversation } from '../../../api/chatApi';

const CareLayer = ({ selectedItem, onClose }) => {
  const { user } = useAuth();
  const [chatLoading, setChatLoading] = useState(false);

  if (!selectedItem) return null;
  const r = selectedItem.raw;

  const isOwner = user && (user.idx === r.userIdx || user.idx === r.userId);

  const dateStr = (r.date || r.careDate)
    ? new Date(r.date || r.careDate).toLocaleString('ko-KR', {
        year: 'numeric', month: 'long', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
      })
    : '';

  const statusLabel = { OPEN: '모집중', IN_PROGRESS: '진행중', COMPLETED: '완료', CANCELLED: '취소' }[r.status] || r.status;

  const handleChat = async () => {
    const ownerId = r.userIdx || r.userId;
    if (!user || !ownerId || user.idx === ownerId) return;
    setChatLoading(true);
    try {
      await getOrCreateDirectConversation(user.idx, ownerId);
      // 채팅 위젯 열기
      window.dispatchEvent(new CustomEvent('openChat', { detail: { userId: ownerId } }));
    } catch (err) {
      alert('채팅 연결에 실패했습니다.');
    } finally {
      setChatLoading(false);
    }
  };

  return (
    <InfoPanel>
      <PanelHeader>
        <TypeBadge>💛 펫케어</TypeBadge>
        <CloseButton onClick={onClose}>✕</CloseButton>
      </PanelHeader>

      <PanelTitle>{selectedItem.title}</PanelTitle>

      <InfoGrid>
        {r.status && (
          <InfoRow>
            <InfoLabel>상태</InfoLabel>
            <StatusBadge $status={r.status}>{statusLabel}</StatusBadge>
          </InfoRow>
        )}
        {dateStr && <InfoRow><InfoLabel>일시</InfoLabel><InfoValue>{dateStr}</InfoValue></InfoRow>}
        {r.address && <InfoRow><InfoLabel>위치</InfoLabel><InfoValue>{r.address}</InfoValue></InfoRow>}
        {r.petName && <InfoRow><InfoLabel>반려동물</InfoLabel><InfoValue>{r.petName}</InfoValue></InfoRow>}
        {r.offeredCoins != null && (
          <InfoRow>
            <InfoLabel>보상</InfoLabel>
            <InfoValue><CoinText>💰 {Number(r.offeredCoins).toLocaleString()} 코인</CoinText></InfoValue>
          </InfoRow>
        )}
        {r.description && (
          <InfoRow>
            <InfoLabel>내용</InfoLabel>
            <InfoValue><Description>{r.description}</Description></InfoValue>
          </InfoRow>
        )}
      </InfoGrid>

      {user && !isOwner && r.status === 'OPEN' && (
        <ActionRow>
          <ChatButton onClick={handleChat} disabled={chatLoading}>
            {chatLoading ? '연결 중...' : '💬 채팅으로 문의하기'}
          </ChatButton>
        </ActionRow>
      )}
      {isOwner && <OwnerBadge>✅ 내가 등록한 케어 요청</OwnerBadge>}
    </InfoPanel>
  );
};

export default CareLayer;

const InfoPanel = styled.div`
  position: absolute;
  bottom: 0;
  right: 0;
  width: 300px;
  max-height: 65vh;
  background: ${props => props.theme.colors.surface};
  border-left: 1px solid ${props => props.theme.colors.border};
  border-top: 1px solid ${props => props.theme.colors.border};
  border-radius: 12px 0 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 500;
  box-shadow: -4px -2px 16px rgba(0,0,0,0.12);

  @media (max-width: 600px) {
    width: 100%;
    border-radius: 12px 12px 0 0;
  }
`;

const PanelHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px 6px;
  flex-shrink: 0;
`;

const TypeBadge = styled.span`
  font-size: 12px;
  color: #FAAD14;
  font-weight: 600;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  color: ${props => props.theme.colors.textSecondary};
  cursor: pointer;
  font-size: 15px;
  padding: 2px 6px;
  border-radius: 4px;
  &:hover { background: ${props => props.theme.colors.surfaceHover}; }
`;

const PanelTitle = styled.h3`
  font-size: 15px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;
  padding: 0 14px 8px;
`;

const InfoGrid = styled.div`
  padding: 0 14px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  overflow-y: auto;
`;

const InfoRow = styled.div`
  display: flex;
  gap: 8px;
  font-size: 13px;
  align-items: flex-start;
`;

const InfoLabel = styled.span`
  color: ${props => props.theme.colors.textSecondary};
  min-width: 48px;
  flex-shrink: 0;
  font-size: 12px;
  padding-top: 1px;
`;

const InfoValue = styled.span`
  color: ${props => props.theme.colors.text};
`;

const StatusBadge = styled.span`
  font-size: 12px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 8px;
  background: ${props => {
    if (props.$status === 'OPEN') return '#e6f7ff';
    if (props.$status === 'IN_PROGRESS') return '#fff7e6';
    if (props.$status === 'COMPLETED') return '#f6ffed';
    return '#f5f5f5';
  }};
  color: ${props => {
    if (props.$status === 'OPEN') return '#1890ff';
    if (props.$status === 'IN_PROGRESS') return '#fa8c16';
    if (props.$status === 'COMPLETED') return '#52c41a';
    return '#999';
  }};
`;

const CoinText = styled.span`
  color: #c47d00;
  font-weight: 600;
`;

const Description = styled.span`
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
`;

const ActionRow = styled.div`
  padding: 10px 14px;
  flex-shrink: 0;
`;

const ChatButton = styled.button`
  width: 100%;
  padding: 9px;
  border-radius: 8px;
  border: none;
  background: #FAAD14;
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:hover:not(:disabled) { opacity: 0.9; }
`;

const OwnerBadge = styled.div`
  padding: 6px 14px;
  font-size: 12px;
  color: #FAAD14;
  font-weight: 600;
`;
