import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { careRequestAdminApi } from '../../../api/careRequestAdminApi';

const CareServiceManagementSection = () => {
  const [status, setStatus] = useState('');
  const [location, setLocation] = useState('');
  const [deleted, setDeleted] = useState('');
  const [q, setQ] = useState('');
  const [careRequests, setCareRequests] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchCareRequests = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {};
      if (status) params.status = status;
      if (location) params.location = location;
      if (deleted !== '') params.deleted = deleted === 'true';
      if (q) params.q = q;
      
      const res = await careRequestAdminApi.listCareRequests(params);
      setCareRequests(res.data || []);
    } catch (e) {
      console.error('케어 요청 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [status, location, deleted, q]);

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
      <Header>
        <Title>케어 서비스 관리</Title>
        <Subtitle>케어 요청, 지원자, 후기, 댓글을 모니터링하고 관리합니다.</Subtitle>
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
        <Group>
          <Label>위치</Label>
          <Input
            placeholder="위치 검색"
            value={location}
            onChange={e => setLocation(e.target.value)}
          />
        </Group>
        <Group>
          <Label>삭제여부</Label>
          <Select value={deleted} onChange={e => setDeleted(e.target.value)}>
            <option value="">전체</option>
            <option value="false">미삭제</option>
            <option value="true">삭제됨</option>
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
          <Refresh onClick={fetchCareRequests}>새로고침</Refresh>
        </Group>
      </Filters>

      <Card>
        {loading && careRequests.length === 0 ? (
          <Info>로딩 중...</Info>
        ) : error ? (
          <Info>{error}</Info>
        ) : careRequests.length === 0 ? (
          <Info>데이터가 없습니다.</Info>
        ) : (
          <Table>
            <thead>
              <tr>
                <th>ID</th>
                <th>작성자</th>
                <th>제목</th>
                <th>상태</th>
                <th>날짜</th>
                <th>삭제됨</th>
                <th>생성일</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {careRequests.map((item) => (
                <tr key={item.idx}>
                  <td>{item.idx}</td>
                  <td>{item.username || '-'}</td>
                  <td className="ellipsis">{item.title || '-'}</td>
                  <td>{item.status || '-'}</td>
                  <td>{item.date ? new Date(item.date).toLocaleDateString() : '-'}</td>
                  <td>{item.deleted ? 'Y' : 'N'}</td>
                  <td>{item.createdAt ? new Date(item.createdAt).toLocaleString() : '-'}</td>
                  <td>
                    <Actions>
                      <Select
                        value={item.status || ''}
                        onChange={e => handleStatusChange(item.idx, e.target.value)}
                        style={{ fontSize: '12px', padding: '4px 8px' }}
                      >
                        {statusOptions.filter(opt => opt.value).map(opt => (
                          <option key={opt.value} value={opt.value}>{opt.label}</option>
                        ))}
                      </Select>
                      {!item.deleted ? (
                        <Danger onClick={() => handleDelete(item.idx)}>삭제</Danger>
                      ) : (
                        <Btn onClick={() => alert('복구 기능은 아직 구현되지 않았습니다.')}>
                          복구
                        </Btn>
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

export default CareServiceManagementSection;

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
  align-items: center;
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
