import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { meetupApi } from '../../../api/meetupApi';
import { useAuth } from '../../../contexts/AuthContext';
import {
  InfoPanel as BaseInfoPanel,
  PanelHeader, CloseButton, PanelTitle, Divider,
  InfoRow, InfoLabel, InfoValue, InfoGrid, ActionRow,
} from '../shared/BaseInfoPanel';

const MeetupLayer = ({ selectedItem, onClose, onRefresh }) => {
  const { user } = useAuth();
  const [participants, setParticipants] = useState([]);
  const [isParticipating, setIsParticipating] = useState(false);
  const [participantLoading, setParticipantLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [meetupDetail, setMeetupDetail] = useState(null);

  useEffect(() => {
    if (!selectedItem) return;
    const idx = selectedItem.raw?.idx;
    if (!idx) return;

    setParticipantLoading(true);
    setMeetupDetail(selectedItem.raw);

    Promise.all([
      meetupApi.getParticipants(idx).catch(() => ({ data: { participants: [] } })),
      meetupApi.checkParticipation(idx).catch(() => ({ data: { isParticipating: false } })),
    ]).then(([pRes, cRes]) => {
      setParticipants(pRes.data?.participants || []);
      setIsParticipating(cRes.data?.isParticipating || false);
    }).finally(() => setParticipantLoading(false));
  }, [selectedItem]);

  if (!selectedItem) return null;
  const r = meetupDetail || selectedItem.raw;
  const isFull = r.currentParticipants >= r.maxParticipants;
  const isOrganizer = user && (user.idx === r.organizerIdx || user.id === r.organizerIdx);

  const dateStr = r.meetupDate || r.date
    ? new Date(r.meetupDate || r.date).toLocaleString('ko-KR', {
        year: 'numeric', month: 'long', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
      })
    : '';

  const handleJoin = async () => {
    setActionLoading(true);
    try {
      await meetupApi.joinMeetup(r.idx);
      setIsParticipating(true);
      const [pRes, mRes] = await Promise.all([
        meetupApi.getParticipants(r.idx),
        meetupApi.getMeetupById(r.idx),
      ]);
      setParticipants(pRes.data?.participants || []);
      setMeetupDetail(mRes.data?.meetup || r);
      onRefresh?.();
    } catch (err) {
      alert(err.response?.data?.error || '참가에 실패했습니다.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleLeave = async () => {
    if (!window.confirm('참가를 취소할까요?')) return;
    setActionLoading(true);
    try {
      await meetupApi.cancelParticipation(r.idx);
      setIsParticipating(false);
      const [pRes, mRes] = await Promise.all([
        meetupApi.getParticipants(r.idx),
        meetupApi.getMeetupById(r.idx),
      ]);
      setParticipants(pRes.data?.participants || []);
      setMeetupDetail(mRes.data?.meetup || r);
      onRefresh?.();
    } catch (err) {
      alert(err.response?.data?.error || '취소에 실패했습니다.');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <InfoPanel>
      <PanelHeader>
        <TypeBadge>🐾 모임</TypeBadge>
        <CloseButton onClick={onClose} aria-label="닫기">✕</CloseButton>
      </PanelHeader>

      <PanelTitle>{r.title}</PanelTitle>

      <InfoGrid>
        {dateStr && <InfoRow><InfoLabel $minWidth="36px">일시</InfoLabel><InfoValue>{dateStr}</InfoValue></InfoRow>}
        {r.location && <InfoRow><InfoLabel $minWidth="36px">장소</InfoLabel><InfoValue>{r.location}</InfoValue></InfoRow>}
        <InfoRow>
          <InfoLabel $minWidth="36px">인원</InfoLabel>
          <InfoValue>
            <ParticipantCount $full={isFull}>
              {r.currentParticipants ?? 0} / {r.maxParticipants ?? 0}명
              {isFull && ' (마감)'}
            </ParticipantCount>
          </InfoValue>
        </InfoRow>
        {r.description && (
          <InfoRow><InfoLabel $minWidth="36px">소개</InfoLabel><InfoValue><Description>{r.description}</Description></InfoValue></InfoRow>
        )}
      </InfoGrid>

      {/* 참가/취소 버튼 */}
      {user && !isOrganizer && (
        <ActionRow>
          {isParticipating ? (
            <LeaveButton onClick={handleLeave} disabled={actionLoading}>
              {actionLoading ? '처리 중...' : '참가 취소'}
            </LeaveButton>
          ) : (
            <JoinButton onClick={handleJoin} disabled={actionLoading || isFull}>
              {actionLoading ? '처리 중...' : isFull ? '마감됨' : '참가하기'}
            </JoinButton>
          )}
        </ActionRow>
      )}
      {isOrganizer && <OrganizerBadge>✅ 내가 만든 모임</OrganizerBadge>}

      {/* 참가자 목록 */}
      <Divider />
      <ParticipantSection>
        <SectionTitle>참가자 {participants.length}명</SectionTitle>
        {participantLoading ? (
          <Hint>불러오는 중...</Hint>
        ) : participants.length === 0 ? (
          <Hint>아직 참가자가 없습니다.</Hint>
        ) : (
          <ParticipantList>
            {participants.map((p, i) => (
              <ParticipantItem key={p.idx || i}>
                👤 {p.nickname || p.username || '참가자'}
              </ParticipantItem>
            ))}
          </ParticipantList>
        )}
      </ParticipantSection>
    </InfoPanel>
  );
};

export default MeetupLayer;

const InfoPanel = styled(BaseInfoPanel)``;

const TypeBadge = styled.span`
  font-size: 12px;
  color: ${props => props.theme.colors.domain.meetup};
  font-weight: 600;
`;

const ParticipantCount = styled.span`
  color: ${props => props.$full ? props.theme.colors.error || '#ef4444' : 'inherit'};
  font-weight: ${props => props.$full ? 600 : 400};
`;

const Description = styled.span`
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
`;

const JoinButton = styled.button`
  width: 100%;
  padding: 9px;
  border-radius: 8px;
  border: none;
  background: ${props => props.theme.colors.domain.meetup};
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:hover:not(:disabled) { opacity: 0.9; }
`;

const LeaveButton = styled.button`
  width: 100%;
  padding: 9px;
  border-radius: 8px;
  border: 1px solid ${props => props.theme.colors.border};
  background: none;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 14px;
  cursor: pointer;
  &:disabled { opacity: 0.5; }
  &:hover:not(:disabled) { background: ${props => props.theme.colors.surfaceHover}; }
`;

const OrganizerBadge = styled.div`
  padding: 6px 14px;
  font-size: 12px;
  color: ${props => props.theme.colors.domain.meetup};
  font-weight: 600;
`;

const ParticipantSection = styled.div`
  padding: 10px 14px;
  flex: 1;
  overflow-y: auto;
`;

const SectionTitle = styled.div`
  font-size: 12px;
  font-weight: 600;
  color: ${props => props.theme.colors.textSecondary};
  margin-bottom: 6px;
`;

const Hint = styled.div`
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
`;

const ParticipantList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const ParticipantItem = styled.div`
  font-size: 13px;
  color: ${props => props.theme.colors.text};
  padding: 3px 0;
`;
