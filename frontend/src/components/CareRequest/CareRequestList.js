import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { careRequestApi } from '../../api/careRequestApi';
import CareRequestForm from './CareRequestForm';
import CareRequestDetailPage from './CareRequestDetailPage';
import { useAuth } from '../../contexts/AuthContext';

const CareRequestList = () => {
  const { user } = useAuth();
  const [careRequests, setCareRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeFilter, setActiveFilter] = useState('ALL');
  const [isCreating, setIsCreating] = useState(false);
  const [successMessage, setSuccessMessage] = useState('');
  const [selectedCareRequestId, setSelectedCareRequestId] = useState(null);

  // API에서 케어 요청 데이터 가져오기
  const fetchCareRequests = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await careRequestApi.getAllCareRequests();
      setCareRequests(response.data || []);
    } catch (error) {
      console.error('케어 요청 데이터 로딩 실패:', error);
      setError('데이터를 불러오는데 실패했습니다.');
      setCareRequests([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCareRequests();
  }, []);

  const filters = [
    { key: 'ALL', label: '전체', count: careRequests.length },
    { key: 'OPEN', label: '모집중', count: careRequests.filter(c => c.status === 'OPEN').length },
    { key: 'IN_PROGRESS', label: '진행중', count: careRequests.filter(c => c.status === 'IN_PROGRESS').length },
    { key: 'COMPLETED', label: '완료', count: careRequests.filter(c => c.status === 'COMPLETED').length }
  ];

  const filteredRequests = activeFilter === 'ALL' 
    ? careRequests 
    : careRequests.filter(request => request.status === activeFilter);

  const handleAddButtonClick = () => {
    if (!user) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }
    setIsCreating(true);
    setSuccessMessage('');
  };

  const handleCareRequestCreated = (createdRequest) => {
    setCareRequests((prev) => [createdRequest, ...prev]);
    setActiveFilter('ALL');
    setIsCreating(false);
    setSuccessMessage('새 펫케어 요청이 등록되었습니다.');
  };

  const handleDeleteRequest = async (requestId) => {
    if (!user) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }
    if (!window.confirm('해당 펫케어 요청을 삭제하시겠습니까?')) {
      return;
    }
    try {
      await careRequestApi.deleteCareRequest(requestId);
      setCareRequests((prev) => prev.filter((request) => request.idx !== requestId));
    } catch (err) {
      const message = err.response?.data?.error || err.message || '삭제에 실패했습니다.';
      alert(message);
    }
  };

  // 필터 변경 시 API 재호출
  const handleFilterChange = async (filterKey) => {
    setActiveFilter(filterKey);
    try {
      setLoading(true);
      setError(null);
      const params = filterKey === 'ALL' ? {} : { status: filterKey };
      const response = await careRequestApi.getAllCareRequests(params);
      setCareRequests(response.data || []);
    } catch (error) {
      console.error('필터링된 데이터 로딩 실패:', error);
      setError('필터링된 데이터를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const getStatusLabel = (status) => {
    switch(status) {
      case 'OPEN': return '모집중';
      case 'IN_PROGRESS': return '진행중';
      case 'COMPLETED': return '완료';
      case 'CANCELLED': return '취소';
      default: return status;
    }
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', { 
      month: 'short', 
      day: 'numeric' 
    });
  };

  if (loading && !isCreating) {
    return <LoadingMessage>펫케어 요청을 불러오는 중...</LoadingMessage>;
  }

  if (isCreating) {
    return (
      <Container>
        <FormHeader>
          <BackButton type="button" onClick={() => setIsCreating(false)}>
            ← 목록으로 돌아가기
          </BackButton>
          <FormTitle>새 펫케어 요청 등록</FormTitle>
          <FormSubtitle>필요한 도움 내용을 자세히 작성하면 매칭 확률이 높아져요.</FormSubtitle>
        </FormHeader>

        <CareRequestForm
          onCancel={() => setIsCreating(false)}
          onCreated={handleCareRequestCreated}
        />
      </Container>
    );
  }

  return (
    <Container>
      <Header>
        <Title>🐾 펫케어 요청</Title>
        <AddButton type="button" onClick={handleAddButtonClick}>
          <span>+</span>
          새 요청 등록
        </AddButton>
      </Header>

      {successMessage && <SuccessBanner>{successMessage}</SuccessBanner>}

      <FilterSection>
        {filters.map(filter => (
          <FilterButton
            key={filter.key}
            active={activeFilter === filter.key}
            onClick={() => handleFilterChange(filter.key)}
          >
            {filter.label} ({filter.count})
          </FilterButton>
        ))}
      </FilterSection>

      <CareGrid>
        {loading ? (
          <LoadingMessage>
            <div className="spinner">⏳</div>
            <h3>데이터를 불러오는 중...</h3>
          </LoadingMessage>
        ) : error ? (
          <ErrorMessage>
            <div className="icon">❌</div>
            <h3>{error}</h3>
            <button onClick={() => window.location.reload()}>
              다시 시도
            </button>
          </ErrorMessage>
        ) : filteredRequests.length === 0 ? (
          <EmptyMessage>
            <div className="icon">🐾</div>
            <h3>등록된 펫케어 요청이 없습니다</h3>
            <p>첫 번째 펫케어 요청을 등록해보세요!</p>
          </EmptyMessage>
        ) : (
          filteredRequests.map(request => (
            <CareCard 
              key={request.idx}
              onClick={() => setSelectedCareRequestId(request.idx)}
            >
              <CardHeader>
                <CardTitle>{request.title}</CardTitle>
                <StatusBadge status={request.status}>
                  {getStatusLabel(request.status)}
                </StatusBadge>
                {user && user.idx === request.userId && (
                  <DeleteButton 
                    type="button" 
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDeleteRequest(request.idx);
                    }}
                  >
                    삭제
                  </DeleteButton>
                )}
              </CardHeader>
              
              <CardDescription>{request.description}</CardDescription>
              
              {/* <CardFooter>
                <UserInfo>
                  <UserAvatar>
                    {request.user.username.charAt(0)}
                  </UserAvatar>
                  <div>
                    <UserName>{request.user.username}</UserName>
                    <div style={{ fontSize: '12px', color: 'var(--text-light)' }}>
                      📍 {request.user.location}
                    </div>
                  </div>
                </UserInfo>
                <DateInfo>
                  <div>{formatDate(request.date)}</div>
                  <div>지원 {request.applications}명</div>
                </DateInfo>
              </CardFooter> */}
            </CareCard>
          ))
        )}
      </CareGrid>

      <CareRequestDetailPage
        isOpen={selectedCareRequestId !== null}
        careRequestId={selectedCareRequestId}
        onClose={() => setSelectedCareRequestId(null)}
        onCommentAdded={() => {
          fetchCareRequests();
        }}
        currentUser={user}
        onCareRequestDeleted={(deletedId) => {
          setCareRequests((prev) => prev.filter((request) => request.idx !== deletedId));
          setSelectedCareRequestId(null);
        }}
      />
    </Container>
  );
};

export default CareRequestList;


const Container = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: ${props => props.theme.spacing.xl} ${props => props.theme.spacing.lg};
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.xl};
  
  @media (max-width: 768px) {
    flex-direction: column;
    gap: ${props => props.theme.spacing.md};
    align-items: stretch;
  }
`;

const Title = styled.h1`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin: 0;
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

const SuccessBanner = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.lg};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  background: rgba(34, 197, 94, 0.15);
  color: ${(props) => props.theme.colors.success || '#166534'};
  border: 1px solid rgba(34, 197, 94, 0.25);
  font-weight: 500;
  font-size: 0.95rem;
`;

const FilterSection = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.md};
  margin-bottom: ${props => props.theme.spacing.xl};
  flex-wrap: wrap;
`;

const FilterButton = styled.button`
  background: ${props => props.active ? props.theme.colors.primary : props.theme.colors.surface};
  color: ${props => props.active ? 'white' : props.theme.colors.text};
  border: 1px solid ${props => props.active ? props.theme.colors.primary : props.theme.colors.border};
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.full};
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: ${props => props.theme.typography.body2.fontSize};
  
  &:hover {
    background: ${props => props.active ? props.theme.colors.primaryDark : props.theme.colors.surfaceHover};
    transform: translateY(-1px);
  }
`;

const CareGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: ${props => props.theme.spacing.lg};
`;

const CareCard = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.lg};
  padding: ${props => props.theme.spacing.lg};
  transition: all 0.3s ease;
  cursor: pointer;
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px ${props => props.theme.colors.shadow};
    border-color: ${props => props.theme.colors.primary};
  }
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: ${props => props.theme.spacing.md};
`;

const CardTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin: 0;
  line-height: 1.4;
  flex: 1;
  margin-right: ${props => props.theme.spacing.sm};
`;

const StatusBadge = styled.span`
  background: ${props => {
    switch(props.status) {
      case 'OPEN': return props.theme.colors.success;
      case 'IN_PROGRESS': return props.theme.colors.warning;
      case 'COMPLETED': return props.theme.colors.textLight;
      default: return props.theme.colors.primary;
    }
  }};
  color: white;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.caption.fontSize};
  font-weight: 600;
  text-transform: uppercase;
`;

const DeleteButton = styled.button`
  margin-left: ${props => props.theme.spacing.sm};
  background: none;
  border: 1px solid ${props => props.theme.colors.error || '#dc2626'};
  color: ${props => props.theme.colors.error || '#dc2626'};
  border-radius: ${props => props.theme.borderRadius.md};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(220, 38, 38, 0.08);
    transform: translateY(-1px);
  }
`;

const CardDescription = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
  line-height: 1.5;
  margin: 0 0 ${props => props.theme.spacing.md} 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
`;

const CardFooter = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: ${props => props.theme.spacing.md};
  padding-top: ${props => props.theme.spacing.md};
  border-top: 1px solid ${props => props.theme.colors.borderLight};
`;

const UserInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
`;

const UserAvatar = styled.div`
  width: 32px;
  height: 32px;
  border-radius: ${props => props.theme.borderRadius.full};
  background: ${props => props.theme.colors.primary};
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 14px;
`;

const UserName = styled.span`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: 500;
`;

const DateInfo = styled.div`
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.caption.fontSize};
  text-align: right;
`;

const LoadingMessage = styled.div`
  text-align: center;
  padding: ${props => props.theme.spacing.xxl};
  color: ${props => props.theme.colors.textSecondary};
  grid-column: 1 / -1;
  
  .spinner {
    font-size: 2rem;
    margin-bottom: ${props => props.theme.spacing.md};
    animation: spin 1s linear infinite;
  }
  
  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }
`;

const ErrorMessage = styled.div`
  text-align: center;
  padding: ${props => props.theme.spacing.xxl};
  color: ${props => props.theme.colors.error};
  grid-column: 1 / -1;
  
  .icon {
    font-size: 3rem;
    margin-bottom: ${props => props.theme.spacing.md};
  }
  
  button {
    margin-top: ${props => props.theme.spacing.md};
    padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
    background: ${props => props.theme.colors.primary};
    color: white;
    border: none;
    border-radius: ${props => props.theme.borderRadius.md};
    cursor: pointer;
    
    &:hover {
      background: ${props => props.theme.colors.primaryDark};
    }
  }
`;

const EmptyMessage = styled.div`
  text-align: center;
  padding: ${props => props.theme.spacing.xxl};
  color: ${props => props.theme.colors.textSecondary};
  grid-column: 1 / -1;
  
  .icon {
    font-size: 48px;
    margin-bottom: ${props => props.theme.spacing.lg};
  }
  
  h3 {
    color: ${props => props.theme.colors.text};
    margin-bottom: ${props => props.theme.spacing.sm};
  }
`;

const FormHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
  margin-bottom: ${(props) => props.theme.spacing.xl};
`;

const BackButton = styled.button`
  align-self: flex-start;
  background: none;
  border: none;
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.95rem;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  padding: ${(props) => props.theme.spacing.xs} 0;
  transition: color 0.2s ease;

  &:hover {
    color: ${(props) => props.theme.colors.primary};
  }
`;

const FormTitle = styled.h2`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
  font-size: ${(props) => props.theme.typography.h2.fontSize};
  font-weight: ${(props) => props.theme.typography.h2.fontWeight};
`;

const FormSubtitle = styled.p`
  margin: 0;
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textSecondary};
`;
