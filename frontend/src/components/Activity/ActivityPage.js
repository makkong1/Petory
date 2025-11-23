import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { activityApi } from '../../api/activityApi';
import { useAuth } from '../../contexts/AuthContext';

const ActivityPage = () => {
  const { user } = useAuth();
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeFilter, setActiveFilter] = useState('ALL');

  useEffect(() => {
    if (user && user.idx) {
      fetchActivities();
    }
  }, [user]);

  const fetchActivities = async () => {
    if (!user || !user.idx) {
      setError('로그인이 필요합니다.');
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await activityApi.getMyActivities(user.idx);
      setActivities(response.data || []);
    } catch (error) {
      console.error('활동 내역 로딩 실패:', error);
      setError('활동 내역을 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const getTypeLabel = (type) => {
    switch (type) {
      case 'CARE_REQUEST': return '펫케어 요청';
      case 'BOARD': return '커뮤니티 게시글';
      case 'MISSING_PET': return '실종 제보';
      case 'CARE_COMMENT': return '펫케어 댓글';
      case 'COMMENT': return '커뮤니티 댓글';
      case 'MISSING_COMMENT': return '실종 제보 댓글';
      case 'LOCATION_REVIEW': return '주변서비스 리뷰';
      default: return type;
    }
  };

  const getTypeIcon = (type) => {
    switch (type) {
      case 'CARE_REQUEST': return '🐾';
      case 'BOARD': return '📝';
      case 'MISSING_PET': return '🔍';
      case 'CARE_COMMENT': return '💬';
      case 'COMMENT': return '💬';
      case 'MISSING_COMMENT': return '💬';
      case 'LOCATION_REVIEW': return '⭐';
      default: return '📌';
    }
  };

  const getTypeColor = (type) => {
    switch (type) {
      case 'CARE_REQUEST': return '#FF7E36';
      case 'BOARD': return '#4A90E2';
      case 'MISSING_PET': return '#E94B3C';
      case 'CARE_COMMENT': return '#FF7E36';
      case 'COMMENT': return '#4A90E2';
      case 'MISSING_COMMENT': return '#E94B3C';
      case 'LOCATION_REVIEW': return '#F5A623';
      default: return '#666';
    }
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now - date;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '방금 전';
    if (minutes < 60) return `${minutes}분 전`;
    if (hours < 24) return `${hours}시간 전`;
    if (days < 7) return `${days}일 전`;
    return date.toLocaleDateString('ko-KR', { 
      year: 'numeric',
      month: 'short', 
      day: 'numeric' 
    });
  };

  const filteredActivities = activeFilter === 'ALL' 
    ? activities 
    : activities.filter(activity => {
        switch (activeFilter) {
          case 'POSTS':
            return ['CARE_REQUEST', 'BOARD', 'MISSING_PET'].includes(activity.type);
          case 'COMMENTS':
            return ['CARE_COMMENT', 'COMMENT', 'MISSING_COMMENT'].includes(activity.type);
          case 'REVIEWS':
            return activity.type === 'LOCATION_REVIEW';
          default:
            return true;
        }
      });

  const filters = [
    { key: 'ALL', label: '전체', count: activities.length },
    { key: 'POSTS', label: '게시글', count: activities.filter(a => ['CARE_REQUEST', 'BOARD', 'MISSING_PET'].includes(a.type)).length },
    { key: 'COMMENTS', label: '댓글', count: activities.filter(a => ['CARE_COMMENT', 'COMMENT', 'MISSING_COMMENT'].includes(a.type)).length },
    { key: 'REVIEWS', label: '리뷰', count: activities.filter(a => a.type === 'LOCATION_REVIEW').length },
  ];

  if (!user) {
    return (
      <Container>
        <EmptyMessage>
          <div className="icon">🔒</div>
          <h3>로그인이 필요합니다</h3>
          <p>내 활동 내역을 보려면 로그인해주세요.</p>
        </EmptyMessage>
      </Container>
    );
  }

  if (loading) {
    return (
      <Container>
        <LoadingMessage>
          <div className="spinner">⏳</div>
          <h3>활동 내역을 불러오는 중...</h3>
        </LoadingMessage>
      </Container>
    );
  }

  if (error) {
    return (
      <Container>
        <ErrorMessage>
          <div className="icon">❌</div>
          <h3>{error}</h3>
          <button onClick={fetchActivities}>다시 시도</button>
        </ErrorMessage>
      </Container>
    );
  }

  return (
    <Container>
      <Header>
        <Title>📋 내 활동</Title>
        <Subtitle>내가 작성한 게시글, 댓글, 리뷰를 한눈에 확인하세요</Subtitle>
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

      <ActivityList>
        {filteredActivities.length === 0 ? (
          <EmptyMessage>
            <div className="icon">📭</div>
            <h3>활동 내역이 없습니다</h3>
            <p>게시글을 작성하거나 댓글을 남겨보세요!</p>
          </EmptyMessage>
        ) : (
          filteredActivities.map(activity => (
            <ActivityCard key={`${activity.type}-${activity.idx}`}>
              <ActivityHeader>
                <TypeBadge color={getTypeColor(activity.type)}>
                  <span className="icon">{getTypeIcon(activity.type)}</span>
                  <span className="label">{getTypeLabel(activity.type)}</span>
                </TypeBadge>
                <DateInfo>{formatDate(activity.createdAt)}</DateInfo>
              </ActivityHeader>
              
              {activity.title && (
                <ActivityTitle>{activity.title}</ActivityTitle>
              )}
              
              {activity.content && (
                <ActivityContent>
                  {activity.content.length > 150 
                    ? `${activity.content.substring(0, 150)}...` 
                    : activity.content}
                </ActivityContent>
              )}

              {activity.relatedTitle && (
                <RelatedInfo>
                  <span className="label">관련:</span>
                  <span className="title">{activity.relatedTitle}</span>
                </RelatedInfo>
              )}

              {activity.status && (
                <StatusBadge status={activity.status}>
                  {activity.status}
                </StatusBadge>
              )}
            </ActivityCard>
          ))
        )}
      </ActivityList>
    </Container>
  );
};

export default ActivityPage;

const Container = styled.div`
  max-width: 1400px;
  margin: 0 auto;
  padding: ${props => props.theme.spacing.xl} ${props => props.theme.spacing.lg};

  @media (max-width: 768px) {
    padding: ${props => props.theme.spacing.md};
  }
`;

const Header = styled.div`
  margin-bottom: ${props => props.theme.spacing.xl};
`;

const Title = styled.h1`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin: 0 0 ${props => props.theme.spacing.sm} 0;

  @media (max-width: 768px) {
    font-size: ${props => props.theme.typography.h3.fontSize};
  }
`;

const Subtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
  margin: 0;
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

const ActivityList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.md};
`;

const ActivityCard = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.lg};
  padding: ${props => props.theme.spacing.lg};
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px ${props => props.theme.colors.shadow};
    border-color: ${props => props.theme.colors.primary};
  }

  @media (max-width: 768px) {
    padding: ${props => props.theme.spacing.md};
  }
`;

const ActivityHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.md};

  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
    gap: ${props => props.theme.spacing.sm};
  }
`;

const TypeBadge = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  background: ${props => props.color}15;
  color: ${props => props.color};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: 600;

  .icon {
    font-size: 1.1em;
  }
`;

const DateInfo = styled.span`
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.caption.fontSize};
`;

const ActivityTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin: 0 0 ${props => props.theme.spacing.sm} 0;
  line-height: 1.4;
`;

const ActivityContent = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
  line-height: 1.6;
  margin: 0 0 ${props => props.theme.spacing.sm} 0;
`;

const RelatedInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  margin-top: ${props => props.theme.spacing.sm};
  padding-top: ${props => props.theme.spacing.sm};
  border-top: 1px solid ${props => props.theme.colors.borderLight};
  font-size: ${props => props.theme.typography.body2.fontSize};

  .label {
    color: ${props => props.theme.colors.textLight};
    font-weight: 500;
  }

  .title {
    color: ${props => props.theme.colors.primary};
    font-weight: 500;
  }
`;

const StatusBadge = styled.span`
  display: inline-block;
  margin-top: ${props => props.theme.spacing.sm};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  background: ${props => {
    switch(props.status) {
      case 'OPEN': case 'ACTIVE': return props.theme.colors.success;
      case 'IN_PROGRESS': return props.theme.colors.warning;
      case 'COMPLETED': return props.theme.colors.textLight;
      default: return props.theme.colors.primary;
    }
  }};
  color: white;
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.caption.fontSize};
  font-weight: 600;
  text-transform: uppercase;
`;

const LoadingMessage = styled.div`
  text-align: center;
  padding: ${props => props.theme.spacing.xxl};
  color: ${props => props.theme.colors.textSecondary};
  
  .spinner {
    font-size: 3rem;
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
  
  .icon {
    font-size: 48px;
    margin-bottom: ${props => props.theme.spacing.lg};
  }
  
  h3 {
    color: ${props => props.theme.colors.text};
    margin-bottom: ${props => props.theme.spacing.sm};
  }
`;

