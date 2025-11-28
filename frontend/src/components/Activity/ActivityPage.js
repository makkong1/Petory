import React, { useState, useEffect, useCallback, useMemo } from 'react';
import styled from 'styled-components';
import { activityApi } from '../../api/activityApi';
import { useAuth } from '../../contexts/AuthContext';

const ActivityPage = () => {
  const { user } = useAuth();
  
  // 서버 사이드 페이징 상태
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  
  // Map + Array 조합: Map으로 빠른 조회/업데이트, Array로 순서 유지
  const [activitiesData, setActivitiesData] = useState({ map: {}, order: [] });
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeFilter, setActiveFilter] = useState('ALL');
  
  // 필터별 개수 (서버에서 받아온 값)
  const [filterCounts, setFilterCounts] = useState({
    allCount: 0,
    postsCount: 0,
    commentsCount: 0,
    reviewsCount: 0,
  });

  // Map + Array를 배열로 변환하는 헬퍼 함수
  const getActivitiesArray = useCallback((activitiesData) => {
    return activitiesData.order.map(id => activitiesData.map[id]).filter(Boolean);
  }, []);

  // 게시글 배열을 Map + Array 구조로 변환하는 헬퍼 함수
  const convertToMapAndOrder = useCallback((activities) => {
    const map = {};
    const order = [];
    activities.forEach(activity => {
      if (activity?.idx && !map[activity.idx]) {
        const key = `${activity.type}-${activity.idx}`;
        map[key] = activity;
        order.push(key);
      }
    });
    return { map, order };
  }, []);

  // 게시글 추가 (중복 체크 포함)
  const addActivitiesToMap = useCallback((existingData, newActivities) => {
    const map = { ...existingData.map };
    const order = [...existingData.order];
    newActivities.forEach(activity => {
      if (activity?.idx) {
        const key = `${activity.type}-${activity.idx}`;
        if (!map[key]) {
          map[key] = activity;
          order.push(key);
        } else {
          // 이미 있으면 업데이트
          map[key] = activity;
        }
      }
    });
    return { map, order };
  }, []);

  // 필터 변경 시 첫 페이지부터 다시 로드
  useEffect(() => {
    if (user && user.idx) {
      fetchActivities(0, true);
    }
  }, [user, activeFilter]);

  const fetchActivities = useCallback(async (pageNum = 0, reset = false) => {
    if (!user || !user.idx) {
      setError('로그인이 필요합니다.');
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      
      const response = await activityApi.getMyActivitiesWithPaging({
        userId: user.idx,
        filter: activeFilter,
        page: pageNum,
        size: pageSize
      });
      
      const pageData = response.data || {};
      const activities = pageData.activities || [];

      if (reset) {
        const newData = convertToMapAndOrder(activities);
        setActivitiesData(newData);
      } else {
        setActivitiesData(prevData => addActivitiesToMap(prevData, activities));
      }

      setTotalCount(pageData.totalCount || 0);
      setHasNext(pageData.hasNext || false);
      setPage(pageNum);
      
      // 필터별 개수 업데이트
      setFilterCounts({
        allCount: pageData.allCount || 0,
        postsCount: pageData.postsCount || 0,
        commentsCount: pageData.commentsCount || 0,
        reviewsCount: pageData.reviewsCount || 0,
      });
    } catch (error) {
      console.error('활동 내역 로딩 실패:', error);
      setError('활동 내역을 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [user, activeFilter, pageSize, convertToMapAndOrder, addActivitiesToMap]);

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

  // 서버에서 이미 필터링되어 오므로 그대로 사용
  const filteredActivities = useMemo(() => {
    return getActivitiesArray(activitiesData);
  }, [activitiesData, getActivitiesArray]);

  const filters = [
    { key: 'ALL', label: '전체', count: filterCounts.allCount },
    { key: 'POSTS', label: '게시글', count: filterCounts.postsCount },
    { key: 'COMMENTS', label: '댓글', count: filterCounts.commentsCount },
    { key: 'REVIEWS', label: '리뷰', count: filterCounts.reviewsCount },
  ];

  // 더 보기 버튼 클릭 핸들러
  const handleLoadMore = useCallback(() => {
    if (!loading && hasNext) {
      fetchActivities(page + 1, false);
    }
  }, [loading, hasNext, page, fetchActivities]);

  // 활동 카드 클릭 핸들러 - 해당 게시글로 이동
  const handleActivityClick = useCallback((activity) => {
    if (!activity) return;

    const { type, idx, relatedId } = activity;
    const targetId = relatedId || idx; // 댓글은 relatedId, 게시글은 idx 사용

    switch (type) {
      case 'BOARD':
      case 'COMMENT':
        // 커뮤니티 게시글로 이동
        if (window.setActiveTab) {
          window.setActiveTab('community');
        }
        // 게시글 상세 모달 열기
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent('openBoardDetail', {
            detail: { boardId: targetId }
          }));
        }, 100);
        break;

      case 'CARE_REQUEST':
      case 'CARE_COMMENT':
        // 펫케어 요청으로 이동
        if (window.setActiveTab) {
          window.setActiveTab('care-requests');
        }
        // 펫케어 요청 상세 열기
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent('openCareRequestDetail', {
            detail: { requestId: targetId }
          }));
        }, 100);
        break;

      case 'MISSING_PET':
      case 'MISSING_COMMENT':
        // 실종 제보로 이동
        if (window.setActiveTab) {
          window.setActiveTab('missing-pets');
        }
        // 실종 제보 상세 열기
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent('openMissingPetDetail', {
            detail: { boardId: targetId }
          }));
        }, 100);
        break;

      case 'LOCATION_REVIEW':
        // 주변서비스로 이동
        if (window.setActiveTab) {
          window.setActiveTab('location-services');
        }
        // 리뷰 상세 열기
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent('openLocationReviewDetail', {
            detail: { reviewId: idx, locationId: relatedId }
          }));
        }, 100);
        break;

      default:
        console.warn('알 수 없는 활동 타입:', type);
    }
  }, []);

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

  if (loading && activitiesData.order.length === 0) {
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
          <button onClick={() => fetchActivities(0, true)}>다시 시도</button>
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
          <>
            {filteredActivities.map(activity => {
              const key = `${activity.type}-${activity.idx}`;
              return (
                <ActivityCard 
                  key={key}
                  onClick={() => handleActivityClick(activity)}
                  clickable
                >
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
              );
            })}
            
            {hasNext && (
              <LoadMoreContainer>
                <LoadMoreButton onClick={handleLoadMore} disabled={loading}>
                  {loading ? '로딩 중...' : `더 보기 (${filteredActivities.length} / ${totalCount})`}
                </LoadMoreButton>
              </LoadMoreContainer>
            )}
          </>
        )}
      </ActivityList>
    </Container>
  );
};

export default ActivityPage;

const Container = styled.div`
  max-width: 1200px;
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
  ${props => props.clickable && `
    cursor: pointer;
  `}
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px ${props => props.theme.colors.shadow};
    border-color: ${props => props.theme.colors.primary};
    ${props => props.clickable && `
      background: ${props.theme.colors.surfaceHover || props.theme.colors.surface};
    `}
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

