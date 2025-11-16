import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { communityAdminApi } from '../../../api/communityAdminApi';

const CommunityManagementSection = () => {
  const [status, setStatus] = useState('ALL'); // ALL | ACTIVE | BLINDED | DELETED
  const [deleted, setDeleted] = useState('');  // '' | 'false' | 'true'
  const [category, setCategory] = useState('ALL');
  const [q, setQ] = useState('');
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchBoards = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await communityAdminApi.listBoards({
        status,
        deleted: deleted === '' ? undefined : deleted === 'true',
        category: category === 'ALL' ? undefined : category,
        q: q || undefined,
      });
      setRows(res.data || []);
    } catch (e) {
      setError('목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBoards();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, deleted, category, q]);

  const onBlind = async (row) => {
    try {
      await communityAdminApi.blindBoard(row.idx);
      fetchBoards();
    } catch {
      alert('블라인드 처리 실패');
    }
  };
  const onUnblind = async (row) => {
    try {
      await communityAdminApi.unblindBoard(row.idx);
      fetchBoards();
    } catch {
      alert('해제 실패');
    }
  };
  const onDeleteSoft = async (row) => {
    if (!window.confirm('이 게시글을 삭제(소프트 삭제)하시겠습니까?')) return;
    try {
      await communityAdminApi.deleteBoard(row.idx);
      fetchBoards();
    } catch {
      alert('삭제 실패');
    }
  };
  const onRestore = async (row) => {
    try {
      await communityAdminApi.restoreBoard(row.idx);
      fetchBoards();
    } catch {
      alert('복구 실패');
    }
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
            <option value="FREE">FREE</option>
            <option value="TIP">TIP</option>
            <option value="QNA">QNA</option>
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
          <Refresh onClick={fetchBoards}>새로고침</Refresh>
        </Group>
      </Filters>

      <Card>
        {loading ? (
          <Info>로딩 중...</Info>
        ) : error ? (
          <Info>{error}</Info>
        ) : rows.length === 0 ? (
          <Info>데이터가 없습니다.</Info>
        ) : (
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
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
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
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
`;

const Danger = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.error};
  color: ${props => props.theme.colors.error};
  background: transparent;
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
`;


