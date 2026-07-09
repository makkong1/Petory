import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { meetupAdminApi } from '../../../api/meetupAdminApi.js';
import PageNavigation from '../../Common/PageNavigation';
import {
  SectionHeader, SectionTitle, SectionSubtitle,
  Toolbar, Spacer, FieldLabel, Select, Search,
  TableWrap, Table, Th, Td, TableMessage,
  StatusPill, RowBtn, RowBtnDanger, RowActions,
} from '../ui/AdminUI';

// 모임 상태 → pill tone/라벨
const MEETUP_STATUS_META = {
  RECRUITING: { tone: 'pending', label: '모집중' },
  FULL: { tone: 'warning', label: '마감' },
  ONGOING: { tone: 'progress', label: '진행중' },
  COMPLETED: { tone: 'success', label: '완료' },
  CANCELLED: { tone: 'neutral', label: '취소됨' },
};

const MeetupManagementSection = () => {
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [meetups, setMeetups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [showParticipants, setShowParticipants] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const PAGE_SIZE = 20;

  const fetchMeetups = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = { page, size: PAGE_SIZE };
      if (status && status !== 'ALL') params.status = status;
      if (q) params.q = q;

      const res = await meetupAdminApi.listMeetups(params);
      setMeetups(res.data.content || []);
      setTotalPages(res.data.totalPages || 0);
    } catch (e) {
      console.error('모임 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [status, q, page]);

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
      <SectionHeader>
        <SectionTitle>산책 모임 관리</SectionTitle>
        <SectionSubtitle>모임과 참여자를 조회하고 관리합니다.</SectionSubtitle>
      </SectionHeader>

      <Toolbar>
        <div><FieldLabel>상태</FieldLabel>
          <Select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
            {statusOptions.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </div>
        <Search
          placeholder="제목/내용/위치/주최자 검색…"
          value={q}
          onChange={e => { setQ(e.target.value); setPage(0); }}
        />
        <Spacer />
        <RowBtn onClick={fetchMeetups}>새로고침</RowBtn>
      </Toolbar>

      {loading && meetups.length === 0 ? (
        <TableMessage>로딩 중...</TableMessage>
      ) : error ? (
        <TableMessage>{error}</TableMessage>
      ) : meetups.length === 0 ? (
        <TableMessage>데이터가 없습니다.</TableMessage>
      ) : (
        <>
          <TableWrap>
            <Table>
              <thead>
                <tr>
                  <Th $align="right">ID</Th>
                  <Th>주최자</Th>
                  <Th>제목</Th>
                  <Th>위치</Th>
                  <Th>날짜</Th>
                  <Th $align="right">인원</Th>
                  <Th $align="center">상태</Th>
                  <Th>생성일</Th>
                  <Th $align="center">액션</Th>
                </tr>
              </thead>
              <tbody>
                {meetups.map((item) => {
                  const meta = MEETUP_STATUS_META[item.status] || { tone: 'neutral', label: item.status || '-' };
                  return (
                    <tr key={item.idx}>
                      <Td $align="right" $mono $muted>#{item.idx}</Td>
                      <Td>{item.organizerName || '-'}</Td>
                      <Td $ellipsis $strong>{item.title || '-'}</Td>
                      <Td $ellipsis={220} $muted>{item.location || '-'}</Td>
                      <Td $muted>{item.date ? new Date(item.date).toLocaleString('ko-KR') : '-'}</Td>
                      <Td $align="right" $mono>{item.currentParticipants || 0} / {item.maxParticipants || 0}</Td>
                      <Td $align="center"><StatusPill $tone={meta.tone}>{meta.label}</StatusPill></Td>
                      <Td $muted>{item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}</Td>
                      <Td $align="center">
                        <RowActions>
                          <RowBtn onClick={() => handleShowParticipants(item.idx)}>참가자</RowBtn>
                          <RowBtnDanger onClick={() => handleDelete(item.idx)}>삭제</RowBtnDanger>
                        </RowActions>
                      </Td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          </TableWrap>

          {totalPages > 1 && (
            <PaginationWrap>
              <PageNavigation
                currentPage={page}
                totalCount={totalPages * PAGE_SIZE}
                pageSize={PAGE_SIZE}
                onPageChange={setPage}
                loading={loading}
                showEdges
                showTotal={false}
              />
            </PaginationWrap>
          )}
        </>
      )}

      {showParticipants && (
        <ModalOverlay onClick={() => setShowParticipants(false)}>
          <ModalContent onClick={e => e.stopPropagation()}>
            <ModalHeader>
              <ModalTitle>참가자 목록</ModalTitle>
              <CloseButton onClick={() => setShowParticipants(false)}>×</CloseButton>
            </ModalHeader>
            <ModalBody>
              {participants.length === 0 ? (
                <TableMessage>참가자가 없습니다.</TableMessage>
              ) : (
                <TableWrap>
                  <Table>
                    <thead>
                      <tr>
                        <Th>사용자명</Th>
                        <Th>참가일시</Th>
                      </tr>
                    </thead>
                    <tbody>
                      {participants.map((p, idx) => (
                        <tr key={idx}>
                          <Td $strong>{p.username || '-'}</Td>
                          <Td $muted>{p.joinedAt ? new Date(p.joinedAt).toLocaleString('ko-KR') : '-'}</Td>
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                </TableWrap>
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



const PaginationWrap = styled.div`
  margin-top: 12px;
`;
