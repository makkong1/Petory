import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { userApi } from '../api/userApi';
import UserModal from './UserModal';

const UserList = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    try {
      setLoading(true);
      console.log('API 호출 시작: GET /api/users');
      const response = await userApi.getAllUsers();
      console.log('API 응답:', response);
      setUsers(response.data);
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
  };

  const handleAddUser = () => {
    setSelectedUser(null);
    setModalOpen(true);
  };

  const handleEditUser = (user) => {
    setSelectedUser(user);
    setModalOpen(true);
  };

  const handleDeleteUser = async (id) => {
    if (window.confirm('정말로 이 유저를 삭제하시겠습니까?')) {
      try {
        await userApi.deleteUser(id);
        fetchUsers(); // 목록 새로고침
      } catch (err) {
        alert('유저 삭제에 실패했습니다.');
        console.error('Error deleting user:', err);
      }
    }
  };

  const handleModalClose = () => {
    setModalOpen(false);
    setSelectedUser(null);
    fetchUsers(); // 목록 새로고침
  };

  return (
    <Container>
      <Header>
        <Title>유저 관리</Title>
        <AddButton onClick={handleAddUser}>
          + 새 유저 추가
        </AddButton>
      </Header>

      {loading ? (
        <LoadingMessage>로딩 중...</LoadingMessage>
      ) : error ? (
        <div>
          <ErrorMessage>{error}</ErrorMessage>
          <div style={{ textAlign: 'center', marginTop: '20px' }}>
            <button 
              onClick={fetchUsers}
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
                    수정
                  </ActionButton>
                  <ActionButton 
                    variant="delete" 
                    onClick={() => handleDeleteUser(user.idx)}
                  >
                    삭제
                  </ActionButton>
                </ButtonGroup>
              </UserCard>
            ))
          )}
        </UserGrid>
      )}

      {modalOpen && (
        <UserModal
          user={selectedUser}
          onClose={handleModalClose}
        />
      )}
    </Container>
  );
};

export default UserList;


const Container = styled.div`
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
`;

const Title = styled.h1`
  color: #333;
  font-size: 28px;
  margin: 0;
`;

const AddButton = styled.button`
  background: #4a90e2;
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s ease;
  
  &:hover {
    background: #357abd;
  }
`;

const UserGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
`;

const UserCard = styled.div`
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  
  &:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
  }
`;

const UserInfo = styled.div`
  margin-bottom: 15px;
`;

const UserName = styled.h3`
  color: #333;
  margin: 0 0 8px 0;
  font-size: 20px;
`;

const UserDetail = styled.p`
  color: #666;
  margin: 4px 0;
  font-size: 14px;
  
  strong {
    color: #333;
  }
`;

const RoleBadge = styled.span`
  background: ${props => {
    switch(props.role) {
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