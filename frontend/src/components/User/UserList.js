import React, { useState, useEffect, useCallback, useMemo } from 'react';
import styled from 'styled-components';
import { userApi } from '../../api/userApi';
import PageNavigation from '../Common/PageNavigation';
import UserStatusModal from './UserStatusModal';

const UserList = ({ showHeader = true }) => {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [usersData, setUsersData] = useState({ map: {}, order: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);

  // Filters (UI만, 백엔드 필터 지원 시 연동)
  const [roleFilter, setRoleFilter] = useState('');
  const [deletedFilter, setDeletedFilter] = useState('');
  const [searchQ, setSearchQ] = useState('');

  const getUsersArray = useCallback((data) => {
    return data.order.map(id => data.map[id]).filter(Boolean);
  }, []);

  const convertToMapAndOrder = useCallback((users) => {
    const map = {};
    const order = [];
    users.forEach(user => {
      if (user?.idx && !map[user.idx]) {
        map[user.idx] = user;
        order.push(user.idx);
      }
    });
    return { map, order };
  }, []);

  const fetchUsers = useCallback(async (pageNum = 0, size = pageSize) => {
    try {
      setLoading(true);
      setError(null);
      const params = { page: pageNum, size };
      if (roleFilter) params.role = roleFilter;
      if (deletedFilter !== '') params.deleted = deletedFilter === 'true';
      if (searchQ) params.q = searchQ;

      const response = await userApi.getAllUsersWithPaging(params);
      const pageData = response.data || {};
      const users = pageData.users || [];
      const newData = convertToMapAndOrder(users);
      setUsersData(newData);
      setTotalCount(pageData.totalCount || 0);
      setPage(pageNum);
    } catch (err) {
      console.error('API 에러:', err);
      setError(`API 호출 실패: ${err.response?.status || 'Network Error'} - ${err.message}`);
    } finally {
      setLoading(false);
    }
  }, [pageSize, roleFilter, deletedFilter, searchQ, convertToMapAndOrder]);

  useEffect(() => {
    fetchUsers(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleEditUser = (user) => {
    setSelectedUser(user);
    setModalOpen(true);
  };

  const handleDeleteUser = async (id) => {
    if (window.confirm('정말로 이 계정을 삭제(소프트 삭제)하시겠습니까?\n삭제된 계정은 복구할 수 있습니다.')) {
      try {
        await userApi.deleteUser(id);
        setUsersData((prev) => {
          if (prev.map[id]) {
            return {
              ...prev,
              map: {
                ...prev.map,
                [id]: { ...prev.map[id], isDeleted: true, deletedAt: new Date().toISOString() }
              }
            };
          }
          return prev;
        });
        fetchUsers(page);
        alert('계정이 삭제되었습니다.');
      } catch (err) {
        alert('계정 삭제에 실패했습니다.');
      }
    }
  };

  const handleRestoreUser = async (id) => {
    if (window.confirm('이 계정을 복구하시겠습니까?')) {
      try {
        await userApi.restoreUser(id);
        setUsersData((prev) => {
          if (prev.map[id]) {
            return {
              ...prev,
              map: {
                ...prev.map,
                [id]: { ...prev.map[id], isDeleted: false, deletedAt: null }
              }
            };
          }
          return prev;
        });
        fetchUsers(page);
        alert('계정이 복구되었습니다.');
      } catch (err) {
        alert('계정 복구에 실패했습니다.');
      }
    }
  };

  const handleModalClose = () => {
    setModalOpen(false);
    setSelectedUser(null);
    fetchUsers(page);
  };

  const handlePageChange = useCallback((newPage) => {
    const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
    if (newPage >= 0 && newPage < totalPages) {
      fetchUsers(newPage);
    }
  }, [totalCount, pageSize, fetchUsers]);

  const handlePageSizeChange = (e) => {
    const newSize = parseInt(e.target.value);
    setPageSize(newSize);
    fetchUsers(0, newSize);
  };

  const users = useMemo(() => getUsersArray(usersData), [usersData, getUsersArray]);

  // 클라이언트 측 필터 (역할, 삭제, 검색 - 백엔드 미지원 시)
  const filteredUsers = useMemo(() => {
    return users.filter(user => {
      if (roleFilter && user.role !== roleFilter) return false;
      if (deletedFilter === 'true' && !user.isDeleted) return false;
      if (deletedFilter === 'false' && user.isDeleted) return false;
      if (searchQ) {
        const q = searchQ.toLowerCase();
        const match = (user.username || '').toLowerCase().includes(q) ||
          (user.id || '').toLowerCase().includes(q) ||
          (user.email || '').toLowerCase().includes(q) ||
          (user.nickname || '').toLowerCase().includes(q);
        if (!match) return false;
      }
      return true;
    });
  }, [users, roleFilter, deletedFilter, searchQ]);

  const formatDateTime = (value) => {
    if (!value) return '-';
    try {
      const d = new Date(value);
      return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString();
    } catch {
      return '-';
    }
  };

  const roleOptions = [
    { value: '', label: '전체' },
    { value: 'USER', label: 'USER' },
    { value: 'SERVICE_PROVIDER', label: 'SERVICE_PROVIDER' },
    { value: 'ADMIN', label: 'ADMIN' },
    { value: 'MASTER', label: 'MASTER' },
  ];

  return (
    <Wrapper>
      {showHeader && (
        <ListHeader>
          <ListTitle>👥 사용자 관리</ListTitle>
          <HeaderRight>
            <PageSizeSelect value={pageSize} onChange={handlePageSizeChange}>
              <option value={20}>20개씩</option>
              <option value={50}>50개씩</option>
              <option value={100}>100개씩</option>
            </PageSizeSelect>
          </HeaderRight>
        </ListHeader>
      )}

      <Filters>
        {!showHeader && (
          <Group>
            <Label>페이지</Label>
            <PageSizeSelect value={pageSize} onChange={handlePageSizeChange}>
              <option value={20}>20개씩</option>
              <option value={50}>50개씩</option>
              <option value={100}>100개씩</option>
            </PageSizeSelect>
          </Group>
        )}
        <Group>
          <Label>역할</Label>
          <Select value={roleFilter} onChange={e => setRoleFilter(e.target.value)}>
            {roleOptions.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </Group>
        <Group>
          <Label>삭제여부</Label>
          <Select value={deletedFilter} onChange={e => setDeletedFilter(e.target.value)}>
            <option value="">전체</option>
            <option value="false">미삭제</option>
            <option value="true">삭제됨</option>
          </Select>
        </Group>
        <Group style={{ flex: 1 }}>
          <Label>검색</Label>
          <Input
            placeholder="아이디/이메일/닉네임"
            value={searchQ}
            onChange={e => setSearchQ(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && fetchUsers(0)}
          />
        </Group>
        <Group>
          <Refresh onClick={() => fetchUsers(0)}>새로고침</Refresh>
        </Group>
      </Filters>

      {totalCount > 0 && (
        <PaginationWrapper>
          <PageNavigation
            currentPage={page}
            totalCount={totalCount}
            pageSize={pageSize}
            onPageChange={handlePageChange}
            loading={loading}
          />
        </PaginationWrapper>
      )}

      <Card>
        {loading && usersData.order.length === 0 ? (
          <Info>로딩 중...</Info>
        ) : error ? (
          <Info>
            {error}
            <RetryButton onClick={() => fetchUsers(0)}>다시 시도</RetryButton>
          </Info>
        ) : filteredUsers.length === 0 ? (
          <Info>등록된 유저가 없습니다.</Info>
        ) : (
          <Table>
            <thead>
              <tr>
                <th>ID</th>
                <th>아이디</th>
                <th>닉네임</th>
                <th>이메일</th>
                <th>역할</th>
                <th>삭제됨</th>
                <th>가입일</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.map(user => (
                <tr key={user.idx}>
                  <td>{user.idx}</td>
                  <td>{user.id || '-'}</td>
                  <td>{user.nickname || user.username || '-'}</td>
                  <td className="ellipsis">{user.email || '-'}</td>
                  <td>
                    <RoleBadge role={user.role}>{user.role}</RoleBadge>
                  </td>
                  <td>{user.isDeleted ? 'Y' : 'N'}</td>
                  <td>{formatDateTime(user.createdAt)}</td>
                  <td>
                    <Actions>
                      <ViewButton onClick={() => handleEditUser(user)}>상태 관리</ViewButton>
                      {!user.isDeleted ? (
                        <DangerButton onClick={() => handleDeleteUser(user.idx)}>삭제</DangerButton>
                      ) : (
                        <ActionBtn onClick={() => handleRestoreUser(user.idx)}>복구</ActionBtn>
                      )}
                    </Actions>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      {modalOpen && (
        <UserStatusModal user={selectedUser} onClose={handleModalClose} />
      )}
    </Wrapper>
  );
};

export default UserList;

const Wrapper = styled.div``;

const ListHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const ListTitle = styled.h1`
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin: 0;
  color: ${props => props.theme.colors.text};
`;

const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.md};
`;

const PageSizeSelect = styled.select`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.sm};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.caption.fontSize};
  cursor: pointer;
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
  width: 200px;
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
  background: ${props => props.theme.colors.surfaceSoft};
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: ${props => props.theme.typography.caption.fontSize};

  thead {
    background: ${props => props.theme.colors.surface};
  }

  th, td {
    padding: 8px 10px;
    border-bottom: 1px solid ${props => props.theme.colors.border};
    text-align: left;
    vertical-align: middle;
  }

  th {
    color: ${props => props.theme.colors.textSecondary};
    font-weight: 600;
    white-space: nowrap;
  }

  td.ellipsis {
    max-width: 200px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
`;

const Info = styled.div`
  padding: ${props => props.theme.spacing.lg};
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${props => props.theme.spacing.md};
`;

const RetryButton = styled.button`
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
`;

const RoleBadge = styled.span`
  display: inline-block;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  color: white;
  background: ${props => {
    switch (props.role) {
      case 'ADMIN': return props.theme.colors.error || '#e74c3c';
      case 'MASTER': return '#9b59b6';
      case 'SERVICE_PROVIDER': return '#f39c12';
      default: return props.theme.colors.primary || '#2ecc71';
    }
  }};
`;

const Actions = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.xs};
  align-items: center;
`;

const ViewButton = styled.button`
  padding: 6px 10px;
  border-radius: ${props => props.theme.borderRadius.sm};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surface};
  cursor: pointer;
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.text};

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const ActionBtn = styled.button`
  padding: 6px 10px;
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  font-size: ${props => props.theme.typography.caption.fontSize};

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const DangerButton = styled.button`
  padding: 6px 10px;
  border: 1px solid ${props => props.theme.colors.error};
  color: ${props => props.theme.colors.error};
  background: transparent;
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  font-size: ${props => props.theme.typography.caption.fontSize};

  &:hover {
    background: rgba(239, 68, 68, 0.1);
  }
`;

const PaginationWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${props => props.theme.spacing.md} 0;
  margin-bottom: ${props => props.theme.spacing.sm};
`;
