import React from 'react';
import styled from 'styled-components';

const ConversationItem = ({ conversation, onClick }) => {
  const { 
    idx, 
    conversationType, 
    title,
    lastMessagePreview, 
    lastMessageAt,
    unreadCount = 0,
    participants = []
  } = conversation;

  // 상대방 정보 찾기 (본인 제외)
  const otherParticipant = participants.find(p => p.status === 'ACTIVE') || participants[0];
  const displayName = title || otherParticipant?.username || '알 수 없음';
  const profileImage = otherParticipant?.profileImageUrl || null;

  // 도메인 뱃지 정보
  const getDomainBadge = () => {
    switch (conversationType) {
      case 'CARE_REQUEST':
        return { label: '펫케어', color: '#FF7E36' };
      case 'MISSING_PET':
        return { label: '실종', color: '#FF9800' };
      case 'DIRECT':
        return { label: '개인간', color: '#4CAF50' };
      case 'GROUP':
        return { label: '단체간', color: '#2196F3' };
      case 'ADMIN_SUPPORT':
        return { label: '관리자', color: '#9C27B0' };
      default:
        return { label: '채팅', color: '#757575' };
    }
  };

  const badge = getDomainBadge();

  // 시간 포맷팅
  const formatTime = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    const now = new Date();
    const diff = now - date;
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));

    if (days === 0) {
      return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
    } else if (days === 1) {
      return '어제';
    } else if (days < 7) {
      return `${days}일 전`;
    } else {
      return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
    }
  };

  return (
    <ItemContainer onClick={onClick}>
      <LeftSection>
        <DomainBadge color={badge.color}>{badge.label}</DomainBadge>
        <ProfileImage>
          {profileImage ? (
            <img src={profileImage} alt={displayName} />
          ) : (
            <DefaultAvatar>{displayName.charAt(0)}</DefaultAvatar>
          )}
        </ProfileImage>
      </LeftSection>
      
      <CenterSection>
        <Name>{displayName}</Name>
        <Preview>{lastMessagePreview || '메시지가 없습니다'}</Preview>
      </CenterSection>
      
      <RightSection>
        <Time>{formatTime(lastMessageAt)}</Time>
        {unreadCount > 0 && (
          <UnreadBadge>{unreadCount > 99 ? '99+' : unreadCount}</UnreadBadge>
        )}
      </RightSection>
    </ItemContainer>
  );
};

export default ConversationItem;

const ItemContainer = styled.div`
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s ease;
  border-bottom: 1px solid ${({ theme }) => theme.colors.borderLight};
  
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
  }
  
  &:last-child {
    border-bottom: none;
  }
`;

const LeftSection = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-right: 12px;
`;

const DomainBadge = styled.span`
  padding: 2px 6px;
  border-radius: 4px;
  background: ${({ color }) => color};
  color: white;
  font-size: 9px;
  font-weight: 600;
  white-space: nowrap;
`;

const ProfileImage = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 50%;
  overflow: hidden;
  background: ${({ theme }) => theme.colors.surface};
  
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  
  @media (max-width: 768px) {
    width: 44px;
    height: 44px;
  }
`;

const DefaultAvatar = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: ${({ theme }) => theme.colors.primary};
  color: white;
  font-size: 18px;
  font-weight: 600;
`;

const CenterSection = styled.div`
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const Name = styled.div`
  font-size: 14px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const Preview = styled.div`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.textSecondary};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const RightSection = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  margin-left: 8px;
`;

const Time = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.textLight};
  white-space: nowrap;
`;

const UnreadBadge = styled.span`
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: ${({ theme }) => theme.colors.primary};
  color: white;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
`;

