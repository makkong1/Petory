import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { missingPetAdminApi } from '../../../api/missingPetAdminApi';

const MissingPetManagementSection = () => {
  const [status, setStatus] = useState('');
  const [deleted, setDeleted] = useState('');
  const [q, setQ] = useState('');
  const [missingPets, setMissingPets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchMissingPets = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {};
      if (status) params.status = status;
      if (deleted !== '') params.deleted = deleted === 'true';
      if (q) params.q = q;
      
      const res = await missingPetAdminApi.listMissingPets(params);
      setMissingPets(res.data || []);
    } catch (e) {
      console.error('실종 제보 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [status, deleted, q]);

  useEffect(() => {
    fetchMissingPets();
  }, [fetchMissingPets]);

  const handleStatusChange = async (id, newStatus) => {
    try {
      await missingPetAdminApi.updateStatus(id, newStatus);
      fetchMissingPets();
    } catch (e) {
      alert(e.response?.data?.message || '상태 변경 실패');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('이 실종 제보를 삭제하시겠습니까?')) return;
    try {
      await missingPetAdminApi.deleteMissingPet(id);
      fetchMissingPets();
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
      <Header>
        <Title>실종/목격 관리</Title>
        <Subtitle>실종/목격 게시글과 댓글을 모니터링하고 상태를 관리합니다.</Subtitle>
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
            placeholder="제목/내용/반려동물 이름/작성자"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </Group>
        <Group>
          <Refresh onClick={fetchMissingPets}>새로고침</Refresh>
        </Group>
      </Filters>

      <Card>
        {loading && missingPets.length === 0 ? (
          <Info>로딩 중...</Info>
        ) : error ? (
          <Info>{error}</Info>
        ) : missingPets.length === 0 ? (
          <Info>데이터가 없습니다.</Info>
        ) : (
          <Table>
            <thead>
              <tr>
                <th>ID</th>
                <th>작성자</th>
                <th>제목</th>
                <th>반려동물 이름</th>
                <th>상태</th>
                <th>삭제됨</th>
                <th>생성일</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {missingPets.map((item) => (
                <tr key={item.idx}>
                  <td>{item.idx}</td>
                  <td>{item.username || '-'}</td>
                  <td className="ellipsis">{item.title || '-'}</td>
                  <td>{item.petName || '-'}</td>
                  <td>{item.status || '-'}</td>
                  <td>{item.deleted ? 'Y' : 'N'}</td>
                  <td>{item.createdAt ? new Date(item.createdAt).toLocaleString() : '-'}</td>
                  <td>
                    <Actions>
                      {item.status !== 'RESOLVED' && (
                        <Btn onClick={() => handleStatusChange(item.idx, 'RESOLVED')}>
                          해결 처리
                        </Btn>
                      )}
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

export default MissingPetManagementSection;

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
