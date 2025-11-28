import React, { useEffect, useState, useCallback, useMemo } from 'react';
import styled from 'styled-components';
import { communityAdminApi } from '../../../api/communityAdminApi';

const CommunityManagementSection = () => {
  const [status, setStatus] = useState('ALL'); // ALL | ACTIVE | BLINDED | DELETED
  const [deleted, setDeleted] = useState('');  // '' | 'false' | 'true'
  const [category, setCategory] = useState('ALL');
  const [q, setQ] = useState('');
  
  // 서버 사이드 페이징 상태
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  
  // Map + Array 조합: Map으로 빠른 조회/업데이트, Array로 순서 유지
  const [boardsData, setBoardsData] = useState({ map: {}, order: [] });
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Map + Array를 배열로 변환하는 헬퍼 함수
  const getBoardsArray = useCallback((boardsData) => {
    return boardsData.order.map(id => boardsData.map[id]).filter(Boolean);
  }, []);

  // 게시글 배열을 Map + Array 구조로 변환하는 헬퍼 함수
  const convertToMapAndOrder = useCallback((boards) => {
    const map = {};
    const order = [];
    boards.forEach(board => {
      if (board?.idx && !map[board.idx]) {
        map[board.idx] = board;
        order.push(board.idx);
      }
    });
    return { map, order };
  }, []);

  // 게시글 추가 (중복 체크 포함)
  const addBoardsToMap = useCallback((existingData, newBoards) => {
    const map = { ...existingData.map };
    const order = [...existingData.order];
    newBoards.forEach(board => {
      if (board?.idx) {
        if (!map[board.idx]) {
          map[board.idx] = board;
          order.push(board.idx);
        } else {
          // 이미 있으면 업데이트
          map[board.idx] = board;
        }
      }
    });
    return { map, order };
  }, []);

  const fetchBoards = useCallback(async (pageNum = 0, reset = false, size = pageSize) => {
    try {
      setLoading(true);
      setError(null);
      const res = await communityAdminApi.listBoardsWithPaging({
        status,
        deleted: deleted === '' ? undefined : deleted === 'true',
        category: category === 'ALL' ? undefined : category,
        q: q || undefined,
        page: pageNum,
        size: size,
      });
      
      const pageData = res.data || {};
      const boards = pageData.boards || [];

      if (reset) {
        const newData = convertToMapAndOrder(boards);
        setBoardsData(newData);
      } else {
        setBoardsData(prevData => addBoardsToMap(prevData, boards));
      }

      setTotalCount(pageData.totalCount || 0);
      setHasNext(pageData.hasNext || false);
      setPage(pageNum);
    } catch (e) {
      console.error('게시글 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [status, deleted, category, q, pageSize, convertToMapAndOrder, addBoardsToMap]);

  useEffect(() => {
    fetchBoards(0, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, deleted, category, q]);

  // 서버에서 이미 필터링되어 오므로 그대로 사용
  const rows = useMemo(() => {
    return getBoardsArray(boardsData);
  }, [boardsData, getBoardsArray]);

  const onBlind = async (row) => {
    if (!row?.idx) {
      alert('게시글 정보가 올바르지 않습니다.');
      return;
    }
    try {
      await communityAdminApi.blindBoard(row.idx);
      // Map에서 해당 게시글 업데이트
      setBoardsData((prev) => {
        if (prev.map[row.idx]) {
          return {
            ...prev,
            map: {
              ...prev.map,
              [row.idx]: { ...prev.map[row.idx], status: 'BLINDED' }
            }
          };
        }
        return prev;
      });
      // 첫 페이지부터 다시 로드
      fetchBoards(0, true);
    } catch (e) {
      console.error('블라인드 처리 실패:', e);
      alert(e.response?.data?.message || '블라인드 처리 실패');
    }
  };
  const onUnblind = async (row) => {
    if (!row?.idx) {
      alert('게시글 정보가 올바르지 않습니다.');
      return;
    }
    try {
      await communityAdminApi.unblindBoard(row.idx);
      // Map에서 해당 게시글 업데이트
      setBoardsData((prev) => {
        if (prev.map[row.idx]) {
          return {
            ...prev,
            map: {
              ...prev.map,
              [row.idx]: { ...prev.map[row.idx], status: 'ACTIVE' }
            }
          };
        }
        return prev;
      });
      // 첫 페이지부터 다시 로드
      fetchBoards(0, true);
    } catch (e) {
      console.error('블라인드 해제 실패:', e);
      alert(e.response?.data?.message || '해제 실패');
    }
  };
  const onDeleteSoft = async (row) => {
    if (!row?.idx) {
      alert('게시글 정보가 올바르지 않습니다.');
      return;
    }
    if (!window.confirm('이 게시글을 삭제(소프트 삭제)하시겠습니까?')) return;
    try {
      await communityAdminApi.deleteBoard(row.idx);
      // Map에서 해당 게시글 제거
      setBoardsData((prev) => {
        const { [row.idx]: removed, ...restMap } = prev.map;
        return {
          map: restMap,
          order: prev.order.filter(id => id !== row.idx),
        };
      });
      // 첫 페이지부터 다시 로드
      fetchBoards(0, true);
    } catch (e) {
      console.error('삭제 실패:', e);
      alert(e.response?.data?.message || '삭제 실패');
    }
  };
  const onRestore = async (row) => {
    if (!row?.idx) {
      alert('게시글 정보가 올바르지 않습니다.');
      return;
    }
    try {
      await communityAdminApi.restoreBoard(row.idx);
      // Map에서 해당 게시글 업데이트
      setBoardsData((prev) => {
        if (prev.map[row.idx]) {
          return {
            ...prev,
            map: {
              ...prev.map,
              [row.idx]: { ...prev.map[row.idx], deleted: false, status: 'ACTIVE' }
            }
          };
        }
        return prev;
      });
      // 첫 페이지부터 다시 로드
      fetchBoards(0, true);
    } catch (e) {
      console.error('복구 실패:', e);
      alert(e.response?.data?.message || '복구 실패');
    }
  };

  // 더 보기 버튼 클릭 핸들러
  const handleLoadMore = useCallback(() => {
    if (!loading && hasNext) {
      fetchBoards(page + 1, false);
    }
  }, [loading, hasNext, page, fetchBoards]);

  // 페이지 크기 변경 핸들러
  const handlePageSizeChange = (e) => {
    const newSize = parseInt(e.target.value);
    setPageSize(newSize);
    fetchBoards(0, true, newSize);
  };

  return (
    <Wrapper>
      <Header>
        <Title>커뮤니티 관리</Title>
        <Subtitle>게시글 상태(블라인드/삭제)와 검색·필터를 지원합니다. (댓글/인기 탭은 다음 단계에서 추가)</Subtitle>
      </Header>

      <Filters>
        <Group>
          <Label>상태</Label>
          <Select value={status} onChange={e => setStatus(e.target.value)}>
            <option value="ALL">전체</option>
            <option value="ACTIVE">게시</option>
            <option value="BLINDED">블라인드</option>
            <option value="DELETED">삭제됨</option>
          </Select>
        </Group>
        <Group>
          <Label>삭제여부</Label>
          <Select value={deleted} onChange={e => setDeleted(e.target.value)}>
            <option value="">전체</option>
            <option value="false">미삭제</option>
            <option value="true">삭제됨</option>
          </Select>
        </Group>
        <Group>
          <Label>카테고리</Label>
          <Select value={category} onChange={e => setCategory(e.target.value)}>
            <option value="ALL">전체</option>
            <option value="일상">일상</option>
            <option value="자랑">자랑</option>
            <option value="질문">질문</option>
            <option value="정보">정보</option>
            <option value="후기">후기</option>
            <option value="모임">모임</option>
            <option value="공지">공지</option>
          </Select>
        </Group>
        <Group style={{ flex: 1 }}>
          <Label>검색</Label>
          <Input
            placeholder="제목/내용/작성자"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </Group>
        <Group>
          <Label>페이지 크기</Label>
          <Select value={pageSize} onChange={handlePageSizeChange}>
            <option value={20}>20개씩</option>
            <option value={50}>50개씩</option>
            <option value={100}>100개씩</option>
          </Select>
        </Group>
        <Group>
          <Refresh onClick={() => fetchBoards(0, true)}>새로고침</Refresh>
        </Group>
      </Filters>

      <Card>
        {loading && boardsData.order.length === 0 ? (
          <Info>로딩 중...</Info>
        ) : error ? (
          <Info>{error}</Info>
        ) : rows.length === 0 ? (
          <Info>데이터가 없습니다.</Info>
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>작성자</th>
                  <th>제목</th>
                  <th>카테고리</th>
                  <th>상태</th>
                  <th>삭제됨</th>
                  <th>생성일</th>
                  <th>액션</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.idx}>
                    <td>{row.idx}</td>
                    <td>{row.username || '-'}</td>
                    <td className="ellipsis">{row.title || '-'}</td>
                    <td>{row.category || '-'}</td>
                    <td>{row.status || '-'}</td>
                    <td>{row.deleted ? 'Y' : 'N'}</td>
                    <td>{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</td>
                    <td>
                      <Actions>
                        {row.status !== 'BLINDED' && !row.deleted && (
                          <Btn onClick={() => onBlind(row)}>블라인드</Btn>
                        )}
                        {row.status === 'BLINDED' && !row.deleted && (
                          <Btn onClick={() => onUnblind(row)}>해제</Btn>
                        )}
                        {!row.deleted ? (
                          <Danger onClick={() => onDeleteSoft(row)}>삭제</Danger>
                        ) : (
                          <Btn onClick={() => onRestore(row)}>복구</Btn>
                        )}
                      </Actions>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
            
            {hasNext && (
              <LoadMoreContainer>
                <LoadMoreButton onClick={handleLoadMore} disabled={loading}>
                  {loading ? '로딩 중...' : `더 보기 (${rows.length} / ${totalCount})`}
                </LoadMoreButton>
              </LoadMoreContainer>
            )}
          </>
        )}
      </Card>
    </Wrapper>
  );
};

export default CommunityManagementSection;

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

const LoadMoreContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${props => props.theme.spacing.xl} 0;
  margin-top: ${props => props.theme.spacing.lg};
`;

const LoadMoreButton = styled.button`
  background: ${props => props.theme.colors.gradient || props.theme.colors.primary};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.xl};
  border-radius: ${props => props.theme.borderRadius.xl};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 12px rgba(255, 126, 54, 0.25);
  
  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(255, 126, 54, 0.35);
  }
  
  &:active:not(:disabled) {
    transform: translateY(0);
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;


