import React, { useState, useEffect } from 'react';
import styled from 'styled-components';

const CareRequestList = () => {
  const [careRequests, setCareRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeFilter, setActiveFilter] = useState('ALL');

  // 임시 데이터 (나중에 API 연동)
  useEffect(() => {
    setTimeout(() => {
      setCareRequests([
        {
          idx: 1,
          title: '주말 강아지 돌봄 부탁드려요',
          description: '2박 3일 출장으로 인해 우리 집 골든리트리버 *몽이*를 돌봐주실 분을 찾습니다. 산책과 밥 주기만 해주시면 됩니다.',
          status: 'OPEN',
          date: '2024-11-02',
          user: { username: '김철수', location: '강남구' },
          applications: 3
        },
        {
          idx: 2,
          title: '고양이 급식 도움 요청',
          description: '1주일 여행 동안 고양이 밥과 화장실 청소를 도와주실 분을 찾습니다.',
          status: 'IN_PROGRESS',
          date: '2024-11-01',
          user: { username: '이영희', location: '서초구' },
          applications: 5
        },
        {
          idx: 3,
          title: '매일 강아지 산책 서비스',
          description: '평일 저녁 시간대에 정기적으로 강아지 산책을 도와주실 분을 찾습니다.',
          status: 'OPEN',
          date: '2024-10-30',
          user: { username: '박민수', location: '송파구' },
          applications: 1
        }
      ]);
      setLoading(false);
    }, 1000);
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

  const getStatusLabel = (status) => {
    switch(status) {
      case 'OPEN': return '모집중';
      case 'IN_PROGRESS': return '진행중';
      case 'COMPLETED': return '완료';
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

  if (loading) {
    return <LoadingMessage>펫케어 요청을 불러오는 중...</LoadingMessage>;
  }

  return (
    <Container>
      <Header>
        <Title>🐾 펫케어 요청</Title>
        <AddButton>
          <span>+</span>
          새 요청 등록
        </AddButton>
      </Header>

      <FilterSection>
        {filters.map(filter => (
          <FilterButton
            key={filter.key}
            active={activeFilter === filter.key}
            onClick={() => setActiveFilter(filter.key)}
          >
            {filter.label} ({filter.count})
          </FilterButton>
        ))}
      </FilterSection>

      <CareGrid>
        {filteredRequests.length === 0 ? (
          <EmptyMessage>
            <div className="icon">🐾</div>
            <h3>등록된 펫케어 요청이 없습니다</h3>
            <p>첫 번째 펫케어 요청을 등록해보세요!</p>
          </EmptyMessage>
        ) : (
          filteredRequests.map(request => (
            <CareCard key={request.idx}>
              <CardHeader>
                <CardTitle>{request.title}</CardTitle>
                <StatusBadge status={request.status}>
                  {getStatusLabel(request.status)}
                </StatusBadge>
              </CardHeader>
              
              <CardDescription>{request.description}</CardDescription>
              
              <CardFooter>
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
              </CardFooter>
            </CareCard>
          ))
        )}
      </CareGrid>
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
  font-size: ${props => props.theme.typography.body1.fontSize};
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
