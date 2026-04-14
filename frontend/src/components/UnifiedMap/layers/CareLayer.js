import React, { useState } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../../contexts/AuthContext';
import { getOrCreateDirectConversation } from '../../../api/chatApi';
import {
  InfoPanel as BaseInfoPanel,
  PanelHeader, CloseButton, PanelTitle,
  InfoRow, InfoLabel, InfoValue, InfoGrid, ActionRow,
} from '../shared/BaseInfoPanel';

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
      await getOrCreateDirectConversation(ownerId);
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
        <CloseButton onClick={onClose} aria-label="닫기">✕</CloseButton>
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

const InfoPanel = styled(BaseInfoPanel)``;

const TypeBadge = styled.span`
  font-size: 12px;
  color: ${props => props.theme.colors.domain.care};
  font-weight: 600;
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
  color: ${props => props.theme.colors.ai.text};
  font-weight: 600;
`;

const Description = styled.span`
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
`;

const ChatButton = styled.button`
  width: 100%;
  padding: 9px;
  border-radius: 8px;
  border: none;
  background: ${props => props.theme.colors.domain.care};
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
  color: ${props => props.theme.colors.domain.care};
  font-weight: 600;
`;
