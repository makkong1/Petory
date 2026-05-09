import React from 'react';
import styled from 'styled-components';

// conversationType → 배지 라벨 + 색상 키 매핑
const DOMAIN_BADGE_MAP = {
  CARE_REQUEST: { label: '펫케어', colorKey: 'care' },
  MISSING_PET:  { label: '실종',   colorKey: 'missing' },
  DIRECT:       { label: '개인간', colorKey: 'personal' },
  GROUP:        { label: '단체간', colorKey: 'group' },
  ADMIN_SUPPORT:{ label: '관리자', colorKey: 'admin' },
};

const ConversationItem = ({ conversation, onClick }) => {
  const {
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

  const badgeInfo = DOMAIN_BADGE_MAP[conversationType] || { label: '채팅', colorKey: 'default' };
  const badge = { label: badgeInfo.label, colorKey: badgeInfo.colorKey };

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
        <DomainBadge colorKey={badge.colorKey}>{badge.label}</DomainBadge>
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
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  cursor: pointer;
  transition: background 200ms ease;
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
  border-radius: ${({ theme }) => theme.borderRadius.sm};
  background: ${({ theme, colorKey }) => {
    switch (colorKey) {
      case 'care':     return theme.colors.domain.care;
      case 'missing':  return theme.colors.domain.missing;
      case 'personal': return theme.colors.success;
      case 'group':    return theme.colors.secondary;
      case 'admin':    return theme.colors.info;
      default:         return theme.colors.textMuted;
    }
  }};
  color: white;
  font-size: ${({ theme }) => theme.typography.tiny.fontSize};
  font-weight: 600;
  white-space: nowrap;
`;

const ProfileImage = styled.div`
  width: 44px;
  height: 44px;
  border-radius: ${({ theme }) => theme.borderRadius.full};
  overflow: hidden;
  background: ${({ theme }) => theme.colors.surfaceSoft};
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  flex-shrink: 0;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
`;

const DefaultAvatar = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: ${({ theme }) => theme.colors.primary};
  color: ${({ theme }) => theme.colors.textInverse};
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
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const Preview = styled.div`
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
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
  font-size: ${({ theme }) => theme.typography.caption.fontSize};
  color: ${({ theme }) => theme.colors.textLight};
  white-space: nowrap;
`;

const UnreadBadge = styled.span`
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  background: ${({ theme }) => theme.colors.primary};
  color: ${({ theme }) => theme.colors.textInverse};
  font-size: ${({ theme }) => theme.typography.caption.fontSize};
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
`;

