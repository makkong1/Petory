import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { careRequestAdminApi } from '../../../api/careRequestAdminApi.js';
import PageNavigation from '../../Common/PageNavigation';
import {
  SectionHeader, SectionTitle, SectionSubtitle,
  Toolbar, Spacer, FieldLabel, Select, Search,
  TableWrap, Table, Th, Td, TableMessage,
  StatusPill, RowBtn, RowBtnDanger, RowActions,
} from '../ui/AdminUI';

// 케어 상태 → pill tone/라벨
const CARE_STATUS_META = {
  OPEN: { tone: 'pending', label: '모집중' },
  IN_PROGRESS: { tone: 'progress', label: '진행중' },
  COMPLETED: { tone: 'success', label: '완료' },
  CANCELLED: { tone: 'neutral', label: '취소됨' },
};

const CareServiceManagementSection = () => {
  const [status, setStatus] = useState('');
  const [location, setLocation] = useState('');
  const [deleted, setDeleted] = useState('');
  const [q, setQ] = useState('');
  const [careRequests, setCareRequests] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const PAGE_SIZE = 20;

  const fetchCareRequests = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = { page, size: PAGE_SIZE };
      if (status) params.status = status;
      if (location) params.location = location;
      if (deleted !== '') params.deleted = deleted === 'true';
      if (q) params.q = q;

      const res = await careRequestAdminApi.listCareRequests(params);
      setCareRequests(res.data.content || []);
      setTotalPages(res.data.totalPages || 0);
    } catch (e) {
      console.error('케어 요청 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [status, location, deleted, q, page]);

  useEffect(() => {
    fetchCareRequests();
  }, [fetchCareRequests]);

  const handleStatusChange = async (id, newStatus) => {
    try {
      await careRequestAdminApi.updateStatus(id, newStatus);
      fetchCareRequests();
    } catch (e) {
      alert(e.response?.data?.message || '상태 변경 실패');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('이 케어 요청을 삭제하시겠습니까?')) return;
    try {
      await careRequestAdminApi.deleteCareRequest(id);
      fetchCareRequests();
    } catch (e) {
      alert(e.response?.data?.message || '삭제 실패');
    }
  };

  const statusOptions = [
    { value: '', label: '전체' },
    { value: 'OPEN', label: '모집중' },
    { value: 'IN_PROGRESS', label: '진행중' },
    { value: 'COMPLETED', label: '완료' },
    { value: 'CANCELLED', label: '취소됨' },
  ];

  return (
    <Wrapper>
      <SectionHeader>
        <SectionTitle>케어 서비스 관리</SectionTitle>
        <SectionSubtitle>케어 요청, 지원자, 후기, 댓글을 모니터링하고 관리합니다.</SectionSubtitle>
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
          placeholder="위치 검색"
          value={location}
          onChange={e => { setLocation(e.target.value); setPage(0); }}
          style={{ minWidth: 140 }}
        />
        <div><FieldLabel>삭제</FieldLabel>
          <Select value={deleted} onChange={e => { setDeleted(e.target.value); setPage(0); }}>
            <option value="">전체</option>
            <option value="false">미삭제</option>
            <option value="true">삭제됨</option>
          </Select>
        </div>
        <Search
          placeholder="제목/내용/작성자 검색…"
          value={q}
          onChange={e => { setQ(e.target.value); setPage(0); }}
        />
        <Spacer />
        <RowBtn onClick={fetchCareRequests}>새로고침</RowBtn>
      </Toolbar>

      {loading && careRequests.length === 0 ? (
        <TableMessage>로딩 중...</TableMessage>
      ) : error ? (
        <TableMessage>{error}</TableMessage>
      ) : careRequests.length === 0 ? (
        <TableMessage>데이터가 없습니다.</TableMessage>
      ) : (
        <>
          <TableWrap>
            <Table>
              <thead>
                <tr>
                  <Th $align="right">ID</Th>
                  <Th>작성자</Th>
                  <Th>제목</Th>
                  <Th $align="center">상태</Th>
                  <Th>날짜</Th>
                  <Th>생성일</Th>
                  <Th $align="center">액션</Th>
                </tr>
              </thead>
              <tbody>
                {careRequests.map((item) => {
                  const meta = item.deleted
                    ? { tone: 'neutral', label: '삭제됨' }
                    : (CARE_STATUS_META[item.status] || { tone: 'neutral', label: item.status || '-' });
                  return (
                    <tr key={item.idx}>
                      <Td $align="right" $mono $muted>#{item.idx}</Td>
                      <Td>{item.username || '-'}</Td>
                      <Td $ellipsis $strong>{item.title || '-'}</Td>
                      <Td $align="center"><StatusPill $tone={meta.tone}>{meta.label}</StatusPill></Td>
                      <Td $muted>{item.date ? new Date(item.date).toLocaleDateString('ko-KR') : '-'}</Td>
                      <Td $muted>{item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}</Td>
                      <Td $align="center">
                        <RowActions>
                          <Select
                            value={item.status || ''}
                            onChange={e => handleStatusChange(item.idx, e.target.value)}
                            style={{ fontSize: '11.5px', padding: '4px 8px' }}
                          >
                            {statusOptions.filter(opt => opt.value).map(opt => (
                              <option key={opt.value} value={opt.value}>{opt.label}</option>
                            ))}
                          </Select>
                          {!item.deleted ? (
                            <RowBtnDanger onClick={() => handleDelete(item.idx)}>삭제</RowBtnDanger>
                          ) : (
                            <RowBtn onClick={() => alert('복구 기능은 아직 구현되지 않았습니다.')}>복구</RowBtn>
                          )}
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
    </Wrapper>
  );
};

export default CareServiceManagementSection;

const Wrapper = styled.div``;


















const PaginationWrap = styled.div`
  margin-top: 12px;
`;
