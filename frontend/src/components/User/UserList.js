import React, { useState, useEffect, useCallback, useMemo } from 'react';
import styled from 'styled-components';
import { userApi } from '../../api/userApi';
import PageNavigation from '../Common/PageNavigation';
import UserStatusModal from './UserStatusModal';

const UserList = () => {
  // 서버 사이드 페이징 상태
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  
  // Map + Array 조합: Map으로 빠른 조회/업데이트, Array로 순서 유지
  const [usersData, setUsersData] = useState({ map: {}, order: [] });
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);

  // Map + Array를 배열로 변환하는 헬퍼 함수
  const getUsersArray = useCallback((usersData) => {
    return usersData.order.map(id => usersData.map[id]).filter(Boolean);
  }, []);

  // 게시글 배열을 Map + Array 구조로 변환하는 헬퍼 함수
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

  // 게시글 추가 (중복 체크 포함)
  const addUsersToMap = useCallback((existingData, newUsers) => {
    const map = { ...existingData.map };
    const order = [...existingData.order];
    newUsers.forEach(user => {
      if (user?.idx) {
        if (!map[user.idx]) {
          map[user.idx] = user;
          order.push(user.idx);
        } else {
          // 이미 있으면 업데이트
          map[user.idx] = user;
        }
      }
    });
    return { map, order };
  }, []);

  useEffect(() => {
    fetchUsers(0);
  }, []);

  const fetchUsers = useCallback(async (pageNum = 0, size = pageSize) => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await userApi.getAllUsersWithPaging({
        page: pageNum,
        size: size
      });
      
      const pageData = response.data || {};
      const users = pageData.users || [];
      const newData = convertToMapAndOrder(users);
      setUsersData(newData);

      setTotalCount(pageData.totalCount || 0);
      setHasNext(pageData.hasNext || false);
      setPage(pageNum);
    } catch (err) {
      console.error('API 에러 상세 정보:', {
        message: err.message,
        status: err.response?.status,
        statusText: err.response?.statusText,
        url: err.config?.url,
        data: err.response?.data
      });
      setError(`API 호출 실패: ${err.response?.status || 'Network Error'} - ${err.message}`);
    } finally {
      setLoading(false);
    }
  }, [pageSize, convertToMapAndOrder]);

  const handleAddUser = () => {
    setSelectedUser(null);
    setModalOpen(true);
  };

  const handleEditUser = (user) => {
    setSelectedUser(user);
    setModalOpen(true);
  };

  const handleDeleteUser = async (id) => {
    if (window.confirm('정말로 이 계정을 삭제(소프트 삭제)하시겠습니까?\n삭제된 계정은 복구할 수 있습니다.')) {
      try {
        await userApi.deleteUser(id);
        // Map에서 해당 사용자 업데이트 (isDeleted 표시)
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
        // 첫 페이지부터 다시 로드
        fetchUsers(0);
        alert('계정이 삭제되었습니다.');
      } catch (err) {
        alert('계정 삭제에 실패했습니다.');
        console.error('Error deleting user:', err);
      }
    }
  };

  const handleRestoreUser = async (id) => {
    if (window.confirm('이 계정을 복구하시겠습니까?')) {
      try {
        await userApi.restoreUser(id);
        // Map에서 해당 사용자 업데이트 (isDeleted 해제)
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
        // 첫 페이지부터 다시 로드
        fetchUsers(0);
        alert('계정이 복구되었습니다.');
      } catch (err) {
        alert('계정 복구에 실패했습니다.');
        console.error('Error restoring user:', err);
      }
    }
  };

  const handleModalClose = () => {
    setModalOpen(false);
    setSelectedUser(null);
    // 첫 페이지부터 다시 로드
    fetchUsers(0);
  };

  const handlePageChange = useCallback((newPage) => {
    const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
    if (newPage >= 0 && newPage < totalPages) {
      fetchUsers(newPage);
    }
  }, [totalCount, pageSize, fetchUsers]);

  // 서버에서 이미 필터링되어 오므로 그대로 사용
  const users = useMemo(() => {
    return getUsersArray(usersData);
  }, [usersData, getUsersArray]);

  // 페이지 크기 변경 핸들러
  const handlePageSizeChange = (e) => {
    const newSize = parseInt(e.target.value);
    setPageSize(newSize);
    fetchUsers(0, newSize);
  };

  return (
    <Container>
      <Header>
        <Title>👥 사용자 관리</Title>
        <HeaderRight>
          <PageSizeSelect value={pageSize} onChange={handlePageSizeChange}>
            <option value={20}>20개씩</option>
            <option value={50}>50개씩</option>
            <option value={100}>100개씩</option>
          </PageSizeSelect>
          <AddButton onClick={handleAddUser}>
            <span>+</span>
            새 유저 추가
          </AddButton>
        </HeaderRight>
      </Header>

      {loading && usersData.order.length === 0 ? (
        <LoadingMessage>로딩 중...</LoadingMessage>
      ) : error ? (
        <div>
          <ErrorMessage>{error}</ErrorMessage>
          <div style={{ textAlign: 'center', marginTop: '20px' }}>
            <button
              onClick={() => fetchUsers(0)}
              style={{
                padding: '10px 20px',
                backgroundColor: '#4a90e2',
                color: 'white',
                border: 'none',
                borderRadius: '6px',
                cursor: 'pointer'
              }}
            >
              다시 시도
            </button>
          </div>
        </div>
      ) : (
        <>
          <UserGrid>
            {users.length === 0 ? (
              <div style={{
                gridColumn: '1 / -1',
                textAlign: 'center',
                padding: '40px',
                color: '#666',
                fontSize: '18px'
              }}>
                등록된 유저가 없습니다. 새 유저를 추가해보세요!
              </div>
            ) : (
              users.map((user) => (
                <UserCard key={user.idx}>
                  <UserInfo>
                    <UserName>{user.username}</UserName>
                    <UserDetail><strong>ID:</strong> {user.id}</UserDetail>
                    <UserDetail><strong>이메일:</strong> {user.email}</UserDetail>
                    <UserDetail><strong>역할:</strong> <RoleBadge role={user.role}>{user.role}</RoleBadge></UserDetail>
                    {user.location && <UserDetail><strong>위치:</strong> {user.location}</UserDetail>}
                    {user.petInfo && <UserDetail><strong>펫 정보:</strong> {user.petInfo}</UserDetail>}
                  </UserInfo>

                  <ButtonGroup>
                    <ActionButton
                      variant="edit"
                      onClick={() => handleEditUser(user)}
                    >
                      상태 관리
                    </ActionButton>
                    {!user.isDeleted ? (
                      <ActionButton
                        variant="delete"
                        onClick={() => handleDeleteUser(user.idx)}
                      >
                        계정 삭제
                      </ActionButton>
                    ) : (
                      <ActionButton
                        variant="restore"
                        onClick={() => handleRestoreUser(user.idx)}
                      >
                        복구
                      </ActionButton>
                    )}
                  </ButtonGroup>
                </UserCard>
              ))
            )}
          </UserGrid>
          
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
        </>
      )}

      {modalOpen && (
        <UserStatusModal
          user={selectedUser}
          onClose={handleModalClose}
        />
      )}
    </Container>
  );
};

export default UserList;


const Container = styled.div`
  padding: ${props => props.theme.spacing.xl} ${props => props.theme.spacing.lg};
  max-width: 1400px;
  margin: 0 auto;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
`;

const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.md};
`;

const Title = styled.h1`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin: 0;
`;

const PageSizeSelect = styled.select`
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover {
    border-color: ${props => props.theme.colors.primary};
  }
  
  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
    box-shadow: 0 0 0 2px rgba(255, 126, 54, 0.1);
  }
`;

const AddButton = styled.button`
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.lg};
  border-radius: ${props => props.theme.borderRadius.lg};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  
  &:hover {
    background: ${props => props.theme.colors.primaryDark};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }
`;

const UserGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
`;

const UserCard = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.lg};
  padding: ${props => props.theme.spacing.lg};
  box-shadow: 0 4px 6px ${props => props.theme.colors.shadow};
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px ${props => props.theme.colors.shadowHover};
    border-color: ${props => props.theme.colors.primary};
  }
`;

const UserInfo = styled.div`
  margin-bottom: 15px;
  color: ${props => props.theme.colors.text};
`;

const UserName = styled.h3`
  color: ${props => props.theme.colors.text};
  margin: 0 0 8px 0;
  font-size: 20px;
`;

const UserDetail = styled.p`
  color: ${props => props.theme.colors.text};
  margin: 4px 0;
  font-size: 14px;
  
  strong {
    color: ${props => props.theme.colors.text};
  }
`;

const RoleBadge = styled.span`
  background: ${props => {
    switch (props.role) {
      case 'ADMIN': return '#e74c3c';
      case 'MASTER': return '#9b59b6';
      case 'SERVICE_PROVIDER': return '#f39c12';
      default: return '#2ecc71';
    }
  }};
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: bold;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 8px;
  margin-top: 15px;
`;

const ActionButton = styled.button`
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.3s ease;
  
  ${props => props.variant === 'edit' && `
    background: #3498db;
    color: white;
    
    &:hover {
      background: #2980b9;
    }
  `}
  
  ${props => props.variant === 'delete' && `
    background: #e74c3c;
    color: white;
    
    &:hover {
      background: #c0392b;
    }
  `}
`;

const LoadingMessage = styled.div`
  text-align: center;
  padding: 40px;
  font-size: 18px;
  color: #666;
`;

const ErrorMessage = styled.div`
  text-align: center;
  padding: 40px;
  font-size: 18px;
  color: #e74c3c;
  background: #fdf2f2;
  border-radius: 8px;
  border: 1px solid #fad5d5;
`;

const PaginationWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${props => props.theme.spacing.xl} 0;
  margin-top: ${props => props.theme.spacing.lg};
`;