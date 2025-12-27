import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { meetupAdminApi } from '../../../api/meetupAdminApi';

const MeetupManagementSection = () => {
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [meetups, setMeetups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedMeetup, setSelectedMeetup] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [showParticipants, setShowParticipants] = useState(false);

  const fetchMeetups = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {};
      if (status && status !== 'ALL') params.status = status;
      if (q) params.q = q;
      
      const res = await meetupAdminApi.listMeetups(params);
      setMeetups(res.data || []);
    } catch (e) {
      console.error('모임 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [status, q]);

  useEffect(() => {
    fetchMeetups();
  }, [fetchMeetups]);

  const handleDelete = async (id) => {
    if (!window.confirm('이 모임을 삭제하시겠습니까?')) return;
    try {
      await meetupAdminApi.deleteMeetup(id);
      fetchMeetups();
    } catch (e) {
      alert(e.response?.data?.message || '삭제 실패');
    }
  };

  const handleShowParticipants = async (id) => {
    try {
      const res = await meetupAdminApi.getParticipants(id);
      setParticipants(res.data || []);
      setSelectedMeetup(id);
      setShowParticipants(true);
    } catch (e) {
      alert(e.response?.data?.message || '참가자 목록 조회 실패');
    }
  };

  const statusOptions = [
    { value: '', label: '전체' },
    { value: 'RECRUITING', label: '모집중' },
    { value: 'FULL', label: '마감' },
    { value: 'ONGOING', label: '진행중' },
    { value: 'COMPLETED', label: '완료' },
    { value: 'CANCELLED', label: '취소됨' },
  ];

  return (
    <Wrapper>
      <Header>
        <Title>산책 모임 관리</Title>
        <Subtitle>모임과 참여자를 조회하고 관리합니다.</Subtitle>
      </Header>

      <Filters>
        <Group>
          <Label>상태</Label>
          <Select value={status} onChange={e => setStatus(e.target.value)}>
            {statusOptions.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </Group>
        <Group style={{ flex: 1 }}>
          <Label>검색</Label>
          <Input
            placeholder="제목/내용/위치/주최자"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </Group>
        <Group>
          <Refresh onClick={fetchMeetups}>새로고침</Refresh>
        </Group>
      </Filters>

      <Card>
        {loading && meetups.length === 0 ? (
          <Info>로딩 중...</Info>
        ) : error ? (
          <Info>{error}</Info>
        ) : meetups.length === 0 ? (
          <Info>데이터가 없습니다.</Info>
        ) : (
          <Table>
            <thead>
              <tr>
                <th>ID</th>
                <th>주최자</th>
                <th>제목</th>
                <th>위치</th>
                <th>날짜</th>
                <th>인원</th>
                <th>상태</th>
                <th>생성일</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {meetups.map((item) => (
                <tr key={item.idx}>
                  <td>{item.idx}</td>
                  <td>{item.organizerName || '-'}</td>
                  <td className="ellipsis">{item.title || '-'}</td>
                  <td className="ellipsis">{item.location || '-'}</td>
                  <td>{item.date ? new Date(item.date).toLocaleString() : '-'}</td>
                  <td>{item.currentParticipants || 0} / {item.maxParticipants || 0}</td>
                  <td>{item.status || '-'}</td>
                  <td>{item.createdAt ? new Date(item.createdAt).toLocaleString() : '-'}</td>
                  <td>
                    <Actions>
                      <Btn onClick={() => handleShowParticipants(item.idx)}>참가자</Btn>
                      <Danger onClick={() => handleDelete(item.idx)}>삭제</Danger>
                    </Actions>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      {showParticipants && (
        <ModalOverlay onClick={() => setShowParticipants(false)}>
          <ModalContent onClick={e => e.stopPropagation()}>
            <ModalHeader>
              <ModalTitle>참가자 목록</ModalTitle>
              <CloseButton onClick={() => setShowParticipants(false)}>×</CloseButton>
            </ModalHeader>
            <ModalBody>
              {participants.length === 0 ? (
                <Info>참가자가 없습니다.</Info>
              ) : (
                <Table>
                  <thead>
                    <tr>
                      <th>사용자명</th>
                      <th>참가일시</th>
                    </tr>
                  </thead>
                  <tbody>
                    {participants.map((p, idx) => (
                      <tr key={idx}>
                        <td>{p.username || '-'}</td>
                        <td>{p.joinedAt ? new Date(p.joinedAt).toLocaleString() : '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              )}
            </ModalBody>
          </ModalContent>
        </ModalOverlay>
      )}
    </Wrapper>
  );
};

export default MeetupManagementSection;

const Wrapper = styled.div``;

const Header = styled.div`
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const Title = styled.h1`
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const Subtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
`;

const Filters = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.md};
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.md};
  flex-wrap: wrap;
`;

const Group = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
`;

const Label = styled.span`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.caption.fontSize};
`;

const Select = styled.select`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.sm};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
`;

const Input = styled.input`
  width: 240px;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.sm};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
`;

const Refresh = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const Card = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.surface};
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: ${props => props.theme.typography.caption.fontSize};
  th, td { padding: 8px 10px; border-bottom: 1px solid ${props => props.theme.colors.border}; }
  th { color: ${props => props.theme.colors.text}; text-align: left; white-space: nowrap; }
  td.ellipsis { max-width: 420px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
`;

const Info = styled.div`
  padding: ${props => props.theme.spacing.lg};
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;

const Actions = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.xs};
`;

const Btn = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const Danger = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.error};
  color: ${props => props.theme.colors.error};
  background: transparent;
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
`;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContent = styled.div`
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.md};
  padding: ${props => props.theme.spacing.lg};
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.md};
`;

const ModalTitle = styled.h3`
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: ${props => props.theme.colors.textSecondary};
  
  &:hover {
    color: ${props => props.theme.colors.text};
  }
`;

const ModalBody = styled.div`
  max-height: 60vh;
  overflow-y: auto;
`;
