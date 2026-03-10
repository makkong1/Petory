import React, { useState, useEffect, useCallback, useRef } from 'react';
import styled from 'styled-components';
import { careRequestApi } from '../../api/careRequestApi';
import { geocodingApi } from '../../api/geocodingApi';
import PageNavigation from '../Common/PageNavigation';
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
  const [searchKeyword, setSearchKeyword] = useState('');
  const [isSearching, setIsSearching] = useState(false);

  // 페이징 상태
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  
  // 위치 필터링 관련 State
  const [filterLocation, setFilterLocation] = useState(null);
  const [locationLoading, setLocationLoading] = useState(false);

  const fetchCareRequests = useCallback(async (pageNum = 0) => {
    try {
      setLoading(true);
      setError(null);
      
      const params = { page: pageNum, size: pageSize };
      if (activeFilter !== 'ALL') {
        params.status = activeFilter;
      }
      if (filterLocation) {
        params.location = filterLocation;
      }
      
      const response = await careRequestApi.getAllCareRequests(params);
      const data = response.data || {};
      setCareRequests(data.careRequests || []);
      setTotalCount(data.totalCount || 0);
      setPage(pageNum);
    } catch (err) {
      console.error('케어 요청 데이터 로딩 실패:', err);
      setError('데이터를 불러오는데 실패했습니다.');
      setCareRequests([]);
    } finally {
      setLoading(false);
    }
  }, [activeFilter, filterLocation, pageSize]);

  useEffect(() => {
    fetchCareRequests(0);
  }, [activeFilter, filterLocation]);

  const handlePageSizeChange = useCallback((newSize) => {
    setPageSize(newSize);
    setPage(0);
  }, []);

  const isInitialMount = useRef(true);
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      return;
    }
    if (isSearching && searchKeyword.trim()) {
      handleSearchWithPage(0);
    } else {
      fetchCareRequests(0);
    }
    // pageSize 변경 시에만 재조회
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageSize]);

  // 전역 이벤트 리스너: 알림에서 펫케어 요청글로 이동할 때 사용
  useEffect(() => {
    const handleOpenCareRequestDetail = (event) => {
      const { careRequestId } = event.detail;
      if (careRequestId) {
        console.log('알림에서 펫케어 요청글 열기:', careRequestId);
        setSelectedCareRequestId(careRequestId);
      }
    };

    window.addEventListener('openCareRequestDetail', handleOpenCareRequestDetail);
    return () => {
      window.removeEventListener('openCareRequestDetail', handleOpenCareRequestDetail);
    };
  }, []);

  const filters = [
    { key: 'ALL', label: '전체' },
    { key: 'OPEN', label: '모집중' },
    { key: 'IN_PROGRESS', label: '진행중' },
    { key: 'COMPLETED', label: '완료' }
  ];

  // 작성일 기준 최신순으로 정렬
  const sortedRequests = [...careRequests].sort((a, b) => {
    const dateA = new Date(a.createdAt || a.date || 0);
    const dateB = new Date(b.createdAt || b.date || 0);
    return dateB - dateA; // 최신 것부터
  });

  const filteredRequests = activeFilter === 'ALL'
    ? sortedRequests
    : sortedRequests.filter(request => request.status === activeFilter);

  const handleSearchWithPage = useCallback(async (pageNum = 0) => {
    if (!searchKeyword.trim()) {
      fetchCareRequests(pageNum);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const response = await careRequestApi.searchCareRequests(searchKeyword.trim(), pageNum, pageSize);
      const data = response.data || {};
      setCareRequests(data.careRequests || []);
      setTotalCount(data.totalCount || 0);
      setPage(pageNum);
    } catch (err) {
      console.error('검색 실패:', err);
      setError('검색 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }, [searchKeyword, pageSize, fetchCareRequests]);

  const handlePageChange = useCallback((newPage) => {
    const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
    if (newPage >= 0 && newPage < totalPages) {
      if (isSearching) {
        handleSearchWithPage(newPage);
      } else {
        fetchCareRequests(newPage);
      }
    }
  }, [totalCount, pageSize, isSearching, fetchCareRequests, handleSearchWithPage]);

  const handleAddButtonClick = () => {
    if (!user) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }
    setIsCreating(true);
    setSuccessMessage('');
  };

  const handleCareRequestCreated = () => {
    setActiveFilter('ALL');
    setIsCreating(false);
    setSuccessMessage('새 펫케어 요청이 등록되었습니다.');
    setPage(0);
    fetchCareRequests(0);
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

  const handleFilterChange = (filterKey) => {
    setActiveFilter(filterKey);
    setSearchKeyword('');
    setIsSearching(false);
    setPage(0);
  };

  // 내 동네 필터 토글
  const handleLocationFilterToggle = () => {
    if (filterLocation) {
      // 이미 켜져있으면 끄기
      setFilterLocation(null);
      return;
    }

    if (!navigator.geolocation) {
      alert('브라우저가 위치 정보를 지원하지 않습니다.');
      return;
    }

    setLocationLoading(true);
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          const { latitude, longitude } = position.coords;
          // 역지오코딩 API 호출
          const addressData = await geocodingApi.coordinatesToAddress(latitude, longitude);
          
          if (addressData && addressData.address) {
            const fullAddress = addressData.address;
            console.log('내 위치 주소:', fullAddress);
            
            // 주소에서 '구' 또는 '군' 단위 추출 (간단한 로직)
            // 예: "서울특별시 강남구 역삼동" -> "강남구"
            // 예: "경기도 성남시 분당구 정자동" -> "분당구"
            const parts = fullAddress.split(' ');
            let targetRegion = '';
            
            // 시/도 다음 단어가 시/군/구일 확률이 높음
            if (parts.length >= 2) {
              // '구'나 '군'이나 '시'로 끝나는 단어 찾기
              // 1. '구' 포함 체크
              const guPart = parts.find(p => p.endsWith('구'));
              if (guPart) {
                targetRegion = guPart;
              } else {
                // 2. '군' 포함 체크
                const gunPart = parts.find(p => p.endsWith('군'));
                if (gunPart) {
                  targetRegion = gunPart;
                } else {
                   // 3. '시' 포함 체크 (시 단위일 경우)
                   const siPart = parts.find(p => p.endsWith('시') && p !== parts[0]); // 첫단어(서울시 등) 제외
                   if (siPart) {
                     targetRegion = siPart;
                   } else {
                     // 찾지 못하면 두번째 단어 사용
                     targetRegion = parts[1];
                   }
                }
              }
            }
            
            if (targetRegion) {
              setFilterLocation(targetRegion);
              console.log('설정된 지역 필터:', targetRegion);
            } else {
              alert('주소에서 지역 정보를 찾을 수 없습니다.');
            }
          } else {
            alert('주소 정보를 가져오는데 실패했습니다.');
          }
        } catch (err) {
          console.error('역지오코딩 에러:', err);
          alert('위치 정보를 변환하는데 실패했습니다.');
        } finally {
          setLocationLoading(false);
        }
      },
      (error) => {
        console.error('위치 권한 에러:', error);
        alert('위치 정보를 가져올 수 없습니다. 권한을 확인해주세요.');
        setLocationLoading(false);
      }
    );
  };

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!searchKeyword.trim()) {
      setIsSearching(false);
      setPage(0);
      fetchCareRequests(0);
      return;
    }
    setIsSearching(true);
    setPage(0);
    handleSearchWithPage(0);
  };

  const handleClearSearch = () => {
    setSearchKeyword('');
    setIsSearching(false);
    setPage(0);
    setActiveFilter('ALL');
  };

  const getStatusLabel = (status) => {
    switch (status) {
      case 'OPEN': return '모집중';
      case 'IN_PROGRESS': return '진행중';
      case 'COMPLETED': return '완료';
      case 'CANCELLED': return '취소';
      default: return status;
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    if (Number.isNaN(date.getTime())) return '-';
    const now = new Date();
    const diff = now - date;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 8640000);

    if (minutes < 1) return '방금 전';
    if (minutes < 60) return `${minutes}분 전`;
    if (hours < 24) return `${hours}시간 전`;
    if (days < 7) return `${days}일 전`;
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

      <SearchSection>
        <SearchForm onSubmit={handleSearch}>
          <SearchInput
            type="text"
            placeholder="제목 또는 내용으로 검색..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
          />
          <SearchButton type="submit">🔍 검색</SearchButton>
          {isSearching && (
            <ClearButton type="button" onClick={handleClearSearch}>
              ✕ 초기화
            </ClearButton>
          )}
        </SearchForm>
        {isSearching && (
          <SearchResultInfo>
            "{searchKeyword}" 검색 결과: {totalCount}개
          </SearchResultInfo>
        )}
      </SearchSection>

      <FilterSection>
        {filters.map(filter => (
          <FilterButton
            key={filter.key}
            active={activeFilter === filter.key}
            onClick={() => handleFilterChange(filter.key)}
          >
            {filter.label}
          </FilterButton>
        ))}
        <LocationFilterButton
          active={!!filterLocation}
          onClick={handleLocationFilterToggle}
          disabled={locationLoading}
        >
          {locationLoading ? '위치 확인 중...' : filterLocation ? `📍 ${filterLocation}만 보기` : '📍 내 동네만 보기'}
        </LocationFilterButton>
      </FilterSection>

      <PageSizeSelector>
        <PageSizeLabel>페이지당 게시글 수:</PageSizeLabel>
        <PageSizeButtons>
          <PageSizeButton active={pageSize === 20} onClick={() => handlePageSizeChange(20)}>
            20
          </PageSizeButton>
          <PageSizeButton active={pageSize === 50} onClick={() => handlePageSizeChange(50)}>
            50
          </PageSizeButton>
          <PageSizeButton active={pageSize === 100} onClick={() => handlePageSizeChange(100)}>
            100
          </PageSizeButton>
        </PageSizeButtons>
      </PageSizeSelector>

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
                <CardTitleSection>
                  <CardTitleRow>
                    <CardTitle>{request.title}</CardTitle>
                    <CardNumber>#{request.idx}</CardNumber>
                  </CardTitleRow>
                </CardTitleSection>
                <CardHeaderRight>
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
                </CardHeaderRight>
              </CardHeader>

              <CardDescription>{request.description}</CardDescription>

              {request?.offeredCoins && request.offeredCoins > 0 && (
                <CoinInfo>
                  <CoinIcon>💰</CoinIcon>
                  <CoinAmount>{request.offeredCoins.toLocaleString()} 코인</CoinAmount>
                </CoinInfo>
              )}

              <CardFooter>
                <AuthorInfo>
                  <AuthorAvatar>
                    {request.username ? request.username.charAt(0).toUpperCase() : 'U'}
                  </AuthorAvatar>
                  <AuthorDetails>
                    <AuthorName>{request.username || '알 수 없음'}</AuthorName>
                    {request.userLocation && (
                      <AuthorLocation>
                        <LocationIcon>📍</LocationIcon>
                        {request.userLocation}
                      </AuthorLocation>
                    )}
                  </AuthorDetails>
                </AuthorInfo>
                <DateInfo>
                  <TimeAgo>{formatDate(request.createdAt || request.date)}</TimeAgo>
                </DateInfo>
              </CardFooter>
            </CareCard>
          ))
        )}
      </CareGrid>

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

      <CareRequestDetailPage
        isOpen={selectedCareRequestId !== null}
        careRequestId={selectedCareRequestId}
        onClose={() => setSelectedCareRequestId(null)}
        onCommentAdded={() => {
          fetchCareRequests(page);
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

const LocationFilterButton = styled(FilterButton)`
  margin-left: auto; /* 우측 정렬 */
  background: ${props => props.active ? '#fff0f5' : props.theme.colors.surface}; /* 핑크빛 배경 */
  color: ${props => props.active ? props.theme.colors.primary : props.theme.colors.text};
  border-color: ${props => props.active ? props.theme.colors.primary : props.theme.colors.border};
  font-weight: 600;
  
  &:hover {
    background: ${props => props.active ? '#ffe4e6' : props.theme.colors.surfaceHover};
  }

  @media (max-width: 768px) {
    margin-left: 0;
    width: 100%;
    justify-content: center;
    border-radius: 8px; /* 모바일에서는 둥글기 좀 줄임 */
  }
`;

const CareGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: ${props => props.theme.spacing.lg};

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: ${props => props.theme.spacing.md};
  }
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
  gap: ${props => props.theme.spacing.sm};
`;

const CardTitleSection = styled.div`
  flex: 1;
  min-width: 0;
`;

const CardTitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  flex-wrap: wrap;
`;

const CardTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin: 0;
  line-height: 1.4;
  flex: 1;
  min-width: 0;
`;

const CardNumber = styled.span`
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: 500;
  white-space: nowrap;
`;

const CardHeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  flex-shrink: 0;
`;

const StatusBadge = styled.span`
  background: ${props => {
    switch (props.status) {
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

const CoinInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  margin: ${props => props.theme.spacing.sm} 0;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  background: ${props => props.theme.colors.surfaceElevated || props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.md};
  width: fit-content;
`;

const CoinIcon = styled.span`
  font-size: 1.1rem;
`;

const CoinAmount = styled.span`
  color: ${props => props.theme.colors.primary};
  font-weight: 600;
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const CardFooter = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: ${props => props.theme.spacing.md};
  padding-top: ${props => props.theme.spacing.md};
  border-top: 1px solid ${props => props.theme.colors.borderLight};
`;

const AuthorInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  flex: 1;
  min-width: 0;
`;

const AuthorAvatar = styled.div`
  width: 36px;
  height: 36px;
  border-radius: ${props => props.theme.borderRadius.full};
  background: ${props => props.theme.colors.primary};
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
`;

const AuthorDetails = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.xs};
  min-width: 0;
`;

const AuthorName = styled.span`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const AuthorLocation = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.caption.fontSize};
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const LocationIcon = styled.span`
  font-size: 12px;
`;

const DateInfo = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: ${props => props.theme.spacing.xs};
  flex-shrink: 0;
`;

const TimeAgo = styled.div`
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.caption.fontSize};
  white-space: nowrap;
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

const SearchSection = styled.div`
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const SearchForm = styled.form`
  display: flex;
  gap: ${props => props.theme.spacing.sm};
  margin-bottom: ${props => props.theme.spacing.sm};
  
  @media (max-width: 768px) {
    flex-direction: column;
  }
`;

const SearchInput = styled.input`
  flex: 1;
  padding: ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body1.fontSize};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  transition: border-color 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
  }

  &::placeholder {
    color: ${props => props.theme.colors.textLight};
  }
`;

const SearchButton = styled.button`
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;

  &:hover {
    background: ${props => props.theme.colors.primaryDark};
    transform: translateY(-1px);
  }
`;

const ClearButton = styled.button`
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body1.fontSize};
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    border-color: ${props => props.theme.colors.primary};
  }
`;

const SearchResultInfo = styled.div`
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
`;

const PaginationWrapper = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${props => props.theme.spacing.md};
  padding: ${props => props.theme.spacing.xl} 0;
  margin-top: ${props => props.theme.spacing.lg};
`;

const PageSizeSelector = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const PageSizeLabel = styled.span`
  font-size: ${props => props.theme.typography.body2.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  font-weight: 500;
`;

const PageSizeButtons = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.xs};
`;

const PageSizeButton = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  background: ${props => props.active ? props.theme.colors.primary : 'transparent'};
  color: ${props => props.active ? 'white' : props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: ${props => props.active ? 600 : 400};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.active ? props.theme.colors.primary : props.theme.colors.background};
    border-color: ${props => props.theme.colors.primary};
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;
