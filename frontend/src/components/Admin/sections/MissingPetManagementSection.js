import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { missingPetAdminApi } from '../../../api/missingPetAdminApi.js';
import PageNavigation from '../../Common/PageNavigation';
import {
  SectionHeader, SectionTitle, SectionSubtitle,
  Toolbar, Spacer, FieldLabel, Select, Search,
  TableWrap, Table, Th, Td, TableMessage,
  StatusPill, RowBtn, RowBtnDanger, RowActions,
} from '../ui/AdminUI';

// 실종/목격 상태 → pill tone/라벨
const missingStatusMeta = (item) => {
  if (item.deleted) return { tone: 'neutral', label: '삭제됨' };
  if (item.status === 'FOUND') return { tone: 'success', label: '목격' };
  if (item.status === 'RESOLVED') return { tone: 'info', label: '해결' };
  return { tone: 'danger', label: '실종' };
};

const MissingPetManagementSection = () => {
  const [status, setStatus] = useState('');
  const [deleted, setDeleted] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [missingPets, setMissingPets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // [리팩토링] listMissingPets → listMissingPetsWithPaging (DB 레벨 필터링 + 페이징)
  const fetchMissingPets = useCallback(async (pageNum = 0) => {
    try {
      setLoading(true);
      setError(null);
      const params = { page: pageNum, size: pageSize };
      if (status) params.status = status;
      if (deleted !== '') params.deleted = deleted === 'true';
      if (q) params.q = q;

      const res = await missingPetAdminApi.listMissingPetsWithPaging(params);
      const data = res.data || {};
      setMissingPets(data.boards || []);
      setTotalCount(data.totalCount || 0);
      setPage(pageNum);
    } catch (e) {
      console.error('실종 제보 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [status, deleted, q, pageSize]);

  useEffect(() => {
    fetchMissingPets(0);
  }, [fetchMissingPets]);

  const handlePageChange = (newPage) => {
    const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
    if (newPage >= 0 && newPage < totalPages) {
      fetchMissingPets(newPage);
    }
  };

  const handleStatusChange = async (id, newStatus) => {
    try {
      await missingPetAdminApi.updateStatus(id, newStatus);
      fetchMissingPets(page);
    } catch (e) {
      alert(e.response?.data?.message || '상태 변경 실패');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('이 실종 제보를 삭제하시겠습니까?')) return;
    try {
      await missingPetAdminApi.deleteMissingPet(id);
      fetchMissingPets(page);
    } catch (e) {
      alert(e.response?.data?.message || '삭제 실패');
    }
  };

  const statusOptions = [
    { value: '', label: '전체' },
    { value: 'MISSING', label: '실종' },
    { value: 'FOUND', label: '목격' },
    { value: 'RESOLVED', label: '해결' },
  ];

  return (
    <Wrapper>
      <SectionHeader>
        <SectionTitle>실종/목격 관리</SectionTitle>
        <SectionSubtitle>실종/목격 게시글과 댓글을 모니터링하고 상태를 관리합니다.</SectionSubtitle>
      </SectionHeader>

      <Toolbar>
        <div><FieldLabel>상태</FieldLabel>
          <Select value={status} onChange={e => setStatus(e.target.value)}>
            {statusOptions.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </div>
        <div><FieldLabel>삭제</FieldLabel>
          <Select value={deleted} onChange={e => setDeleted(e.target.value)}>
            <option value="">전체</option>
            <option value="false">미삭제</option>
            <option value="true">삭제됨</option>
          </Select>
        </div>
        <Search
          placeholder="제목/내용/반려동물 이름/작성자 검색…"
          value={q}
          onChange={e => setQ(e.target.value)}
        />
        <Spacer />
        <RowBtn onClick={() => fetchMissingPets(page)}>새로고침</RowBtn>
      </Toolbar>

      {loading && missingPets.length === 0 ? (
        <TableMessage>로딩 중...</TableMessage>
      ) : error ? (
        <TableMessage>{error}</TableMessage>
      ) : missingPets.length === 0 ? (
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
                  <Th>반려동물</Th>
                  <Th $align="center">상태</Th>
                  <Th>생성일</Th>
                  <Th $align="center">액션</Th>
                </tr>
              </thead>
              <tbody>
                {missingPets.map((item) => {
                  const meta = missingStatusMeta(item);
                  return (
                    <tr key={item.idx}>
                      <Td $align="right" $mono $muted>#{item.idx}</Td>
                      <Td>{item.username || '-'}</Td>
                      <Td $ellipsis $strong>{item.title || '-'}</Td>
                      <Td>{item.petName || '-'}</Td>
                      <Td $align="center"><StatusPill $tone={meta.tone}>{meta.label}</StatusPill></Td>
                      <Td $muted>{item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}</Td>
                      <Td $align="center">
                        <RowActions>
                          {item.status !== 'RESOLVED' && !item.deleted && (
                            <RowBtn onClick={() => handleStatusChange(item.idx, 'RESOLVED')}>해결 처리</RowBtn>
                          )}
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

          {totalCount > 0 && (
            <PaginationWrap>
              <PageNavigation
                currentPage={page}
                totalCount={totalCount}
                pageSize={pageSize}
                onPageChange={handlePageChange}
                loading={loading}
                showEdges
              />
            </PaginationWrap>
          )}
        </>
      )}
    </Wrapper>
  );
};

export default MissingPetManagementSection;

const Wrapper = styled.div``;

const PaginationWrap = styled.div`
  margin-top: ${p => p.theme.spacing.md};
`;
















